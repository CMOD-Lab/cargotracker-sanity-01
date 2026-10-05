package org.eclipse.cargotracker.interfaces.handling.file;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
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
 * Reads handling event CSV files from an Amazon S3 bucket instead of the local file system.
 *
 * <p><strong>Cloud-readiness fix (rule cr-java-0064 – Local Directory Scanning):</strong>
 * The original implementation used {@code uploadDirectory.listFiles()} (line 47 of the source)
 * to enumerate files on the local file system. This pattern fails in containerised / cloud
 * environments where the file system is ephemeral and directory structures are unpredictable.
 *
 * <p>The fix replaces every local-FS operation with Amazon S3 equivalents:
 * <ul>
 *   <li>{@code uploadDirectory.listFiles()} → {@link #listAllS3Objects(String)} using
 *       {@code ListObjectsV2} with full pagination (continuation tokens) so that buckets
 *       with more than 1 000 objects are handled correctly.</li>
 *   <li>{@code new RandomAccessFile(file, "r")} → {@code S3Client.getObject()} streaming
 *       download into an in-memory {@code BufferedReader}.</li>
 *   <li>Post-processing file deletion → {@code S3Client.deleteObject()}.</li>
 * </ul>
 *
 * <p>The S3 bucket name is resolved from the batch job property {@code upload_directory}.
 * The AWS region is read from the environment variable {@code AWS_REGION} (defaults to
 * {@code us-east-1} when not set).
 */
@Dependent
@Named("EventItemReader")
public class EventItemReader extends AbstractItemReader {

  /** Batch job property whose value is the S3 bucket name for uploaded event files. */
  private static final String UPLOAD_DIRECTORY = "upload_directory";

  @Inject private Logger logger;

  @Inject private JobContext jobContext;

  private EventFilesCheckpoint checkpoint;

  // -------------------------------------------------------------------------
  // S3 state (replaces java.io.File / RandomAccessFile local-FS fields)
  // -------------------------------------------------------------------------
  private S3Client s3Client;
  private String s3BucketName;

  /** All S3 object keys discovered in the upload bucket for this job execution. */
  private List<String> s3Keys;

  /** Index into {@link #s3Keys} for the object currently being read. */
  private int currentKeyIndex;

  /** Lines of the currently loaded S3 object. */
  private List<String> currentLines;

  /** Index into {@link #currentLines} for the next line to return. */
  private int currentLineIndex;

  // -------------------------------------------------------------------------
  // AbstractItemReader lifecycle
  // -------------------------------------------------------------------------

  @Override
  public void open(Serializable checkpoint) throws Exception {
    // Resolve S3 bucket name from job property (replaces: new File(uploadDirectory) at line 37)
    s3BucketName = jobContext.getProperties().getProperty(UPLOAD_DIRECTORY);

    String awsRegion = System.getenv("AWS_REGION");
    if (awsRegion == null || awsRegion.isEmpty()) {
      awsRegion = "us-east-1";
    }

    // Build S3Client — replaces java.io.File usage at lines 37, 45, 47 of the original source
    s3Client = S3Client.builder()
        .region(Region.of(awsRegion))
        .build();

    if (checkpoint == null) {
      this.checkpoint = new EventFilesCheckpoint();
      logger.log(Level.INFO, "Scanning S3 upload bucket: {0}", s3BucketName);

      // cr-java-0064 fix: replace uploadDirectory.listFiles() with ListObjectsV2 + pagination
      s3Keys = listAllS3Objects(s3BucketName);

      if (s3Keys.isEmpty()) {
        logger.log(Level.INFO, "No files found in S3 bucket: {0}", s3BucketName);
      } else {
        logger.log(Level.INFO, "Found {0} file(s) in S3 bucket", s3Keys.size());
        this.checkpoint.setFiles(new ArrayList<>(s3Keys));
      }
      currentKeyIndex = 0;
      currentLines = null;
      currentLineIndex = 0;
    } else {
      logger.log(Level.INFO, "Resuming from previous checkpoint");
      this.checkpoint = (EventFilesCheckpoint) checkpoint;
      // Re-discover keys; resume from the checkpointed index
      s3Keys = listAllS3Objects(s3BucketName);
      currentKeyIndex = this.checkpoint.getKeyIndex();
      currentLines = null;
      currentLineIndex = 0;
    }

    // Load the first (or resumed) S3 object into memory
    loadNextS3Object();
  }

  // -------------------------------------------------------------------------
  // S3 helpers
  // -------------------------------------------------------------------------

  /**
   * Lists <em>all</em> object keys in the given S3 bucket using paginated
   * {@code ListObjectsV2} calls.
   *
   * <p>This replaces {@code uploadDirectory.listFiles()} (line 47 of the original source).
   * Unlike a single {@code listFiles()} call, this implementation correctly handles buckets
   * that contain more than 1 000 objects by following continuation tokens until the response
   * reports {@code isTruncated() == false}.
   *
   * @param bucketName the S3 bucket to enumerate
   * @return an ordered list of all object keys found in the bucket
   */
  private List<String> listAllS3Objects(String bucketName) {
    List<String> keys = new ArrayList<>();
    try {
      String continuationToken = null;
      do {
        ListObjectsV2Request.Builder requestBuilder = ListObjectsV2Request.builder()
            .bucket(bucketName);
        if (continuationToken != null) {
          requestBuilder.continuationToken(continuationToken);
        }

        ListObjectsV2Response response = s3Client.listObjectsV2(requestBuilder.build());

        for (S3Object s3Object : response.contents()) {
          keys.add(s3Object.key());
        }

        // Follow pagination if the result set was truncated
        continuationToken = response.isTruncated() ? response.nextContinuationToken() : null;

      } while (continuationToken != null);

    } catch (Exception ex) {
      logger.log(Level.WARNING, "Could not list objects in S3 bucket: " + bucketName, ex);
    }
    return keys;
  }

  /**
   * Downloads the S3 object at {@link #currentKeyIndex} and loads its lines into memory.
   *
   * <p>Replaces {@code new RandomAccessFile(file, "r")} (line 45 of the original source).
   */
  private void loadNextS3Object() {
    if (s3Keys == null || currentKeyIndex >= s3Keys.size()) {
      currentLines = null;
      return;
    }

    String key = s3Keys.get(currentKeyIndex);
    logger.log(Level.INFO, "Processing S3 object: s3://{0}/{1}",
        new Object[]{s3BucketName, key});

    try {
      GetObjectRequest getRequest = GetObjectRequest.builder()
          .bucket(s3BucketName)
          .key(key)
          .build();

      ResponseInputStream<GetObjectResponse> s3ObjectStream = s3Client.getObject(getRequest);
      currentLines = new ArrayList<>();
      try (BufferedReader reader = new BufferedReader(
          new InputStreamReader(s3ObjectStream, StandardCharsets.UTF_8))) {
        String line;
        while ((line = reader.readLine()) != null) {
          currentLines.add(line);
        }
      }
      currentLineIndex = 0;
    } catch (Exception ex) {
      logger.log(Level.WARNING, "Could not read S3 object: " + key, ex);
      currentLines = null;
    }
  }

  // -------------------------------------------------------------------------
  // AbstractItemReader chunk processing
  // -------------------------------------------------------------------------

  @Override
  public Object readItem() throws Exception {
    if (currentLines == null) {
      return null; // No more data
    }

    // Advance to the next non-empty line within the current S3 object
    while (currentLineIndex < currentLines.size()) {
      String line = currentLines.get(currentLineIndex);
      currentLineIndex++;
      if (line != null && !line.trim().isEmpty()) {
        return parseLine(line);
      }
    }

    // Finished reading the current S3 object — delete it from S3 and advance
    String processedKey = s3Keys.get(currentKeyIndex);
    logger.log(Level.INFO, "Finished processing S3 object, deleting: s3://{0}/{1}",
        new Object[]{s3BucketName, processedKey});
    try {
      s3Client.deleteObject(DeleteObjectRequest.builder()
          .bucket(s3BucketName)
          .key(processedKey)
          .build());
    } catch (Exception ex) {
      logger.log(Level.WARNING, "Could not delete S3 object: " + processedKey, ex);
    }

    currentKeyIndex++;
    checkpoint.setKeyIndex(currentKeyIndex);

    if (currentKeyIndex >= s3Keys.size()) {
      logger.log(Level.INFO, "No more S3 objects to process in bucket: {0}", s3BucketName);
      currentLines = null;
      return null;
    }

    loadNextS3Object();
    return readItem();
  }

  // -------------------------------------------------------------------------
  // CSV parsing
  // -------------------------------------------------------------------------

  private Object parseLine(String line) throws EventLineParseException {
    String[] result = line.split(",");

    if (result.length != 5) {
      throw new EventLineParseException("Wrong number of data elements", line);
    }

    LocalDateTime completionTime;
    try {
      completionTime = DateConverter.toDateTime(result[0]);
    } catch (DateTimeParseException e) {
      throw new EventLineParseException("Cannot parse completion time", e, line);
    }

    TrackingId trackingId;
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

    UnLocode unLocode;
    try {
      unLocode = new UnLocode(result[3]);
    } catch (IllegalArgumentException | NullPointerException e) {
      throw new EventLineParseException("Cannot parse UN location code", e, line);
    }

    HandlingEvent.Type eventType;
    try {
      eventType = HandlingEvent.Type.valueOf(result[4]);
    } catch (IllegalArgumentException | NullPointerException e) {
      throw new EventLineParseException("Cannot parse event type", e, line);
    }

    return new HandlingEventRegistrationAttempt(
        LocalDateTime.now(), completionTime, trackingId, voyageNumber, eventType, unLocode);
  }

  // -------------------------------------------------------------------------
  // Checkpoint support
  // -------------------------------------------------------------------------

  @Override
  public Serializable checkpointInfo() throws Exception {
    return this.checkpoint;
  }
}
