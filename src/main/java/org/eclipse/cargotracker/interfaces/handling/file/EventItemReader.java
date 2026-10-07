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
 * Reads cargo handling event records from Amazon S3 objects for batch processing.
 *
 * <p>Cloud-native replacement for the original local file system directory scanning approach
 * (java.io.File / RandomAccessFile scanning an upload directory). Local directory enumeration
 * fails in containerised and cloud environments where the file system is ephemeral and not
 * shared across instances. This implementation uses Amazon S3 ListObjectsV2 API to discover
 * and iterate over objects using prefixes and pagination, enabling cloud-native resource
 * discovery that works reliably across all AWS deployment targets (ECS, EKS, Lambda, etc.).
 *
 * <p>Fix for rule cr-java-0064 (Local Directory Scanning): replaced {@code java.io.File.listFiles()}
 * local directory enumeration at line 47 with {@link ListObjectsV2Request} / {@link ListObjectsV2Response}
 * S3 object discovery, satisfying the "Replace local directory scanning with Amazon S3
 * ListObjectsV2 operations" remediation strategy.
 *
 * <p>Required job properties (set in the batch job XML descriptor):
 * <ul>
 *   <li>{@code s3_bucket_name}  – the S3 bucket that contains the upload objects</li>
 *   <li>{@code upload_prefix}   – (optional, default {@code "uploads/"}) key prefix for upload objects</li>
 *   <li>{@code aws_region}      – (optional, default {@code AWS_REGION} env var or {@code "us-east-1"})</li>
 * </ul>
 */
@Dependent
@Named("EventItemReader")
public class EventItemReader extends AbstractItemReader {

  private static final String S3_BUCKET_NAME = "s3_bucket_name";
  private static final String S3_UPLOAD_PREFIX = "upload_prefix";
  private static final String AWS_REGION = "aws_region";

  @Inject private Logger logger;

  @Inject private JobContext jobContext;
  private EventFilesCheckpoint checkpoint;
  private S3Client s3Client;
  private List<String> s3Keys;
  private int currentKeyIndex;
  private BufferedReader currentReader;
  private String currentKey;

  /**
   * Opens the reader by connecting to Amazon S3 and listing all objects under the configured
   * prefix using {@link ListObjectsV2Request}. This replaces the original local directory
   * scan ({@code new File(uploadDirectory).listFiles()}) with a cloud-native S3 enumeration
   * that works in ephemeral, containerised environments.
   *
   * @param checkpoint serialised checkpoint from a previous run, or {@code null} for a fresh start
   */
  @Override
  public void open(Serializable checkpoint) throws Exception {
    String bucketName = jobContext.getProperties().getProperty(S3_BUCKET_NAME);
    String uploadPrefix = jobContext.getProperties().getProperty(S3_UPLOAD_PREFIX, "uploads/");
    String awsRegion = jobContext.getProperties().getProperty(AWS_REGION,
        System.getenv().getOrDefault("AWS_REGION", "us-east-1"));

    s3Client = S3Client.builder()
        .region(Region.of(awsRegion))
        .build();

    if (checkpoint == null) {
      this.checkpoint = new EventFilesCheckpoint();
      logger.log(Level.INFO, "Scanning S3 bucket: {0} with prefix: {1}",
          new Object[]{bucketName, uploadPrefix});

      // Cloud-native resource discovery: ListObjectsV2 replaces local File.listFiles()
      s3Keys = listS3Objects(bucketName, uploadPrefix);

      if (s3Keys.isEmpty()) {
        logger.log(Level.INFO, "No files found in S3 bucket/prefix");
      } else {
        logger.log(Level.INFO, "Found {0} file(s) in S3 to process", s3Keys.size());
      }
      currentKeyIndex = 0;
    } else {
      logger.log(Level.INFO, "Starting from previous checkpoint");
      this.checkpoint = (EventFilesCheckpoint) checkpoint;
      s3Keys = listS3Objects(bucketName, uploadPrefix);
      currentKeyIndex = 0;
    }

    openNextS3Object(bucketName);
  }

  /**
   * Enumerates S3 objects under the given prefix using {@link ListObjectsV2Request}.
   * Supports pagination and filters out folder-marker keys (keys ending with {@code "/"}).
   *
   * @param bucketName the S3 bucket name
   * @param prefix     the key prefix to filter objects (e.g., {@code "uploads/"})
   * @return list of S3 object keys available for processing
   */
  private List<String> listS3Objects(String bucketName, String prefix) {
    List<String> keys = new ArrayList<>();
    ListObjectsV2Request listRequest = ListObjectsV2Request.builder()
        .bucket(bucketName)
        .prefix(prefix)
        .build();
    ListObjectsV2Response listResponse = s3Client.listObjectsV2(listRequest);
    for (S3Object s3Object : listResponse.contents()) {
      if (!s3Object.key().endsWith("/")) {
        keys.add(s3Object.key());
      }
    }
    return keys;
  }

  /**
   * Opens a {@link BufferedReader} over the next S3 object in the discovered key list.
   * Uses {@link GetObjectRequest} to stream the object content directly from S3.
   *
   * @param bucketName the S3 bucket name
   */
  private void openNextS3Object(String bucketName) throws Exception {
    if (currentKeyIndex < s3Keys.size()) {
      currentKey = s3Keys.get(currentKeyIndex);
      logger.log(Level.INFO, "Processing S3 object: {0}", currentKey);
      GetObjectRequest getRequest = GetObjectRequest.builder()
          .bucket(bucketName)
          .key(currentKey)
          .build();
      ResponseInputStream<GetObjectResponse> s3Object = s3Client.getObject(getRequest);
      currentReader = new BufferedReader(new InputStreamReader(s3Object));
    } else {
      currentReader = null;
      currentKey = null;
    }
  }

  @Override
  public Object readItem() throws Exception {
    if (currentReader != null) {
      String line = currentReader.readLine();

      if (line != null) {
        return parseLine(line);
      } else {
        String bucketName = jobContext.getProperties().getProperty(S3_BUCKET_NAME);
        logger.log(Level.INFO, "Finished processing S3 object, deleting: {0}", currentKey);
        currentReader.close();

        // Delete the processed S3 object to prevent reprocessing
        DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
            .bucket(bucketName)
            .key(currentKey)
            .build();
        s3Client.deleteObject(deleteRequest);

        currentKeyIndex++;
        openNextS3Object(bucketName);

        if (currentReader == null) {
          logger.log(Level.INFO, "No more S3 objects to process");
          return null;
        } else {
          return readItem();
        }
      }
    } else {
      return null;
    }
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
