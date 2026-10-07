package org.eclipse.cargotracker.interfaces.handling.file;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import jakarta.batch.api.chunk.AbstractItemReader;
import jakarta.batch.runtime.context.JobContext;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.eclipse.cargotracker.application.util.DateConverter;
import org.eclipse.cargotracker.domain.model.cargo.TrackingId;
import org.eclipse.cargotracker.domain.model.handling.HandlingEvent;
import org.eclipse.cargotracker.domain.model.location.UnLocode;
import org.eclipse.cargotracker.domain.model.voyage.VoyageNumber;
import org.eclipse.cargotracker.interfaces.handling.HandlingEventRegistrationAttempt;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;

/**
 * Reads handling event lines from files stored in Amazon S3.
 *
 * <p>Replaces local file system directory enumeration ({@code File.listFiles()}) with
 * Amazon S3 {@code ListObjectsV2} API calls, enabling cloud-native resource discovery
 * in environments where the local file system is ephemeral and unpredictable.
 *
 * <p>Uses prefix-based filtering and full pagination (via {@code continuationToken}) so
 * that buckets containing more than 1 000 objects are handled correctly.
 *
 * <p>Required job properties:
 * <ul>
 *   <li>{@code s3_bucket_name}   – name of the S3 bucket that holds upload objects</li>
 *   <li>{@code s3_upload_prefix} – S3 key prefix (folder) for files to be processed
 *       (defaults to {@code "upload/"} when not set)</li>
 *   <li>{@code aws_region}       – AWS region of the bucket (defaults to {@code "us-east-1"})</li>
 * </ul>
 */
@Dependent
@Named("EventItemReader")
public class EventItemReader extends AbstractItemReader {

  private static final String S3_BUCKET_NAME   = "s3_bucket_name";
  private static final String S3_UPLOAD_PREFIX = "s3_upload_prefix";
  private static final String AWS_REGION       = "aws_region";
  private static final String DEFAULT_PREFIX   = "upload/";
  private static final String DEFAULT_REGION   = "us-east-1";

  @Inject private Logger logger;

  @Inject private JobContext jobContext;

  private EventFilesCheckpoint checkpoint;

  /** S3 client – created once per open() call and reused across readItem() calls. */
  private S3Client s3Client;

  /** Lines buffered from the current S3 object being processed. */
  private List<String> currentLines;

  /** Index into {@link #currentLines} for the next line to return. */
  private int currentLineIndex;

  /** S3 key of the object currently being read. */
  private String currentObjectKey;

  @Override
  public void open(Serializable checkpoint) throws Exception {
    String bucketName   = jobContext.getProperties().getProperty(S3_BUCKET_NAME);
    String uploadPrefix = jobContext.getProperties().getProperty(S3_UPLOAD_PREFIX, DEFAULT_PREFIX);
    String awsRegion    = jobContext.getProperties().getProperty(AWS_REGION, DEFAULT_REGION);

    s3Client = S3Client.builder()
        .region(Region.of(awsRegion))
        .build();

    if (checkpoint == null) {
      this.checkpoint = new EventFilesCheckpoint();
      logger.log(Level.INFO, "Scanning S3 bucket: {0}, prefix: {1}",
          new Object[]{bucketName, uploadPrefix});

      // Replace local directory scanning (File.listFiles()) with S3 ListObjectsV2
      // operations that use prefix-based filtering and pagination to discover all
      // objects in the bucket regardless of how many there are.
      List<String> s3Keys = listS3ObjectsWithPagination(bucketName, uploadPrefix);

      if (s3Keys.isEmpty()) {
        logger.log(Level.INFO, "No files found in S3 upload prefix: {0}", uploadPrefix);
      } else {
        logger.log(Level.INFO, "Found {0} file(s) to process in S3 prefix: {1}",
            new Object[]{s3Keys.size(), uploadPrefix});
        // Store S3 object keys directly in the checkpoint – no java.io.File wrapping needed.
        this.checkpoint.setFiles(s3Keys);
      }
    } else {
      logger.log(Level.INFO, "Starting from previous checkpoint");
      this.checkpoint = (EventFilesCheckpoint) checkpoint;
    }

    loadCurrentS3Object(bucketName);
  }

  /**
   * Lists ALL S3 object keys under the given prefix using paginated
   * {@code ListObjectsV2} requests.
   *
   * <p>Each {@code ListObjectsV2} response returns at most 1 000 objects. When
   * {@code isTruncated()} is {@code true} the response includes a
   * {@code nextContinuationToken} that must be passed to the subsequent request.
   * This loop continues until the response is no longer truncated, ensuring every
   * object under the prefix is discovered regardless of bucket size.
   *
   * @param bucketName the S3 bucket to scan
   * @param prefix     the key prefix (logical "directory") to enumerate
   * @return an ordered list of all matching S3 object keys
   */
  private List<String> listS3ObjectsWithPagination(String bucketName, String prefix) {
    List<String> keys = new ArrayList<>();
    String resolvedBucket = resolveBucketName(bucketName);

    ListObjectsV2Request.Builder requestBuilder = ListObjectsV2Request.builder()
        .bucket(resolvedBucket)
        .prefix(prefix);

    String continuationToken = null;

    do {
      if (continuationToken != null) {
        requestBuilder.continuationToken(continuationToken);
      }

      ListObjectsV2Response response = s3Client.listObjectsV2(requestBuilder.build());

      for (S3Object s3Object : response.contents()) {
        // Skip "directory" placeholder objects (keys ending with '/')
        if (!s3Object.key().endsWith("/")) {
          keys.add(s3Object.key());
        }
      }

      // Advance to the next page when the result set is truncated
      if (Boolean.TRUE.equals(response.isTruncated())) {
        continuationToken = response.nextContinuationToken();
        logger.log(Level.FINE, "S3 ListObjectsV2 result truncated; fetching next page with token: {0}",
            continuationToken);
      } else {
        continuationToken = null;
      }

    } while (continuationToken != null);

    return keys;
  }

  /**
   * Loads lines from the current S3 object into {@link #currentLines}.
   * If there is no current object in the checkpoint, sets currentLines to null.
   */
  private void loadCurrentS3Object(String bucketName) throws Exception {
    String currentKey = this.checkpoint.currentFile();

    if (currentKey == null) {
      logger.log(Level.INFO, "No files to process");
      currentLines = null;
      currentObjectKey = null;
      return;
    }

    currentObjectKey = currentKey;
    String resolvedBucket = resolveBucketName(bucketName);

    logger.log(Level.INFO, "Processing S3 object: s3://{0}/{1}",
        new Object[]{resolvedBucket, currentObjectKey});

    GetObjectRequest getRequest = GetObjectRequest.builder()
        .bucket(resolvedBucket)
        .key(currentObjectKey)
        .build();

    ResponseInputStream<GetObjectResponse> s3ObjectStream = s3Client.getObject(getRequest);
    currentLines = new ArrayList<>();
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(s3ObjectStream))) {
      String line;
      while ((line = reader.readLine()) != null) {
        currentLines.add(line);
      }
    }

    // Restore position from checkpoint
    currentLineIndex = (int) this.checkpoint.getFilePointer();
  }

  @Override
  public Object readItem() throws Exception {
    if (currentLines == null) {
      return null;
    }

    String bucketName = jobContext.getProperties().getProperty(S3_BUCKET_NAME);

    if (currentLineIndex < currentLines.size()) {
      String line = currentLines.get(currentLineIndex);
      currentLineIndex++;
      this.checkpoint.setFilePointer(currentLineIndex);
      return parseLine(line);
    } else {
      // Finished current S3 object – delete it from S3 and advance the checkpoint.
      logger.log(Level.INFO, "Finished processing S3 object, deleting: {0}", currentObjectKey);
      deleteS3Object(resolveBucketName(bucketName), currentObjectKey);

      // Advance the checkpoint to the next S3 object key.
      String nextKey = this.checkpoint.nextFile();
      if (nextKey == null) {
        logger.log(Level.INFO, "No more files to process");
        currentLines = null;
        return null;
      } else {
        loadCurrentS3Object(bucketName);
        return readItem();
      }
    }
  }

  /**
   * Deletes an object from S3 after it has been fully processed.
   */
  private void deleteS3Object(String bucketName, String key) {
    try {
      DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
          .bucket(bucketName)
          .key(key)
          .build();
      s3Client.deleteObject(deleteRequest);
      logger.log(Level.INFO, "Deleted S3 object: s3://{0}/{1}", new Object[]{bucketName, key});
    } catch (Exception ex) {
      logger.log(Level.WARNING, "Failed to delete S3 object: " + key, ex);
    }
  }

  /**
   * Resolves the bucket name from the job property, falling back to the
   * {@code S3_BUCKET_NAME} environment variable when the property is blank.
   */
  private String resolveBucketName(String propertyValue) {
    if (propertyValue != null && !propertyValue.isBlank()) {
      return propertyValue;
    }
    String envBucket = System.getenv("S3_BUCKET_NAME");
    if (envBucket != null && !envBucket.isBlank()) {
      return envBucket;
    }
    throw new IllegalStateException(
        "S3 bucket name is not configured. Set the '" + S3_BUCKET_NAME
            + "' job property or the S3_BUCKET_NAME environment variable.");
  }

  private Object parseLine(String line) throws EventLineParseException {
    String[] result = line.split(",");

    if (result.length != 5) {
      throw new EventLineParseException("Wrong number of data elements", line);
    }

    LocalDateTime completionTime = null;

    try {
      completionTime = DateConverter.toDateTime(result[0]);
    } catch (DateTimeParseException e) {
      throw new EventLineParseException("Cannot parse completion time", e, line);
    }

    TrackingId trackingId = null;

    try {
      trackingId = new TrackingId(result[1]);
    } catch (NullPointerException e) {
      throw new EventLineParseException("Cannot parse tracking ID", e, line);
    }

    VoyageNumber voyageNumber = null;

    try {
      if (!result[2].isEmpty()) {
        voyageNumber = new VoyageNumber(result[2]);
      }
    } catch (NullPointerException e) {
      throw new EventLineParseException("Cannot parse voyage number", e, line);
    }

    UnLocode unLocode = null;

    try {
      unLocode = new UnLocode(result[3]);
    } catch (IllegalArgumentException | NullPointerException e) {
      throw new EventLineParseException("Cannot parse UN location code", e, line);
    }

    HandlingEvent.Type eventType = null;

    try {
      eventType = HandlingEvent.Type.valueOf(result[4]);
    } catch (IllegalArgumentException | NullPointerException e) {
      throw new EventLineParseException("Cannot parse event type", e, line);
    }

    HandlingEventRegistrationAttempt attempt =
        new HandlingEventRegistrationAttempt(
            LocalDateTime.now(), completionTime, trackingId, voyageNumber, eventType, unLocode);

    return attempt;
  }

  @Override
  public Serializable checkpointInfo() throws Exception {
    return this.checkpoint;
  }
}
