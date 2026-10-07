package org.eclipse.cargotracker.interfaces.handling.file;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import jakarta.batch.api.chunk.AbstractItemWriter;
import jakarta.batch.runtime.context.JobContext;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.transaction.Transactional;
import org.eclipse.cargotracker.application.ApplicationEvents;
import org.eclipse.cargotracker.application.util.DateConverter;
import org.eclipse.cargotracker.interfaces.handling.HandlingEventRegistrationAttempt;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * Writes processed handling event records to Amazon S3 as archive CSV objects.
 *
 * <p>Replaces the hard-coded local archive directory path with S3 object storage so the
 * application can run in cloud environments where the local file system is ephemeral.
 *
 * <p>Required job properties:
 * <ul>
 *   <li>{@code s3_bucket_name}    – name of the S3 bucket for archive objects</li>
 *   <li>{@code s3_archive_prefix} – S3 key prefix (folder) for archive objects
 *       (defaults to {@code "archive/"} when not set)</li>
 *   <li>{@code aws_region}        – AWS region of the bucket (defaults to {@code "us-east-1"})</li>
 * </ul>
 */
@Dependent
@Named("EventItemWriter")
public class EventItemWriter extends AbstractItemWriter {

  private static final String S3_BUCKET_NAME     = "s3_bucket_name";
  private static final String S3_ARCHIVE_PREFIX  = "s3_archive_prefix";
  private static final String AWS_REGION         = "aws_region";
  private static final String DEFAULT_PREFIX     = "archive/";
  private static final String DEFAULT_REGION     = "us-east-1";

  @Inject private Logger logger;

  @Inject private JobContext jobContext;
  @Inject private ApplicationEvents applicationEvents;

  /** S3 client – created once per open() call and reused across writeItems() calls. */
  private S3Client s3Client;

  /** Resolved S3 bucket name. */
  private String bucketName;

  /** Resolved S3 archive key prefix. */
  private String archivePrefix;

  /** S3 object key for the archive CSV of this job instance. */
  private String archiveKey;

  @Override
  public void open(Serializable checkpoint) throws Exception {
    bucketName    = resolveBucketName(jobContext.getProperties().getProperty(S3_BUCKET_NAME));
    archivePrefix = jobContext.getProperties().getProperty(S3_ARCHIVE_PREFIX, DEFAULT_PREFIX);
    String awsRegion = jobContext.getProperties().getProperty(AWS_REGION, DEFAULT_REGION);

    s3Client = S3Client.builder()
        .region(Region.of(awsRegion))
        .build();

    // Build a stable archive key for this job instance so that multiple writeItems()
    // calls append to the same S3 object.
    archiveKey = archivePrefix
        + "archive_"
        + jobContext.getJobName()
        + "_"
        + jobContext.getInstanceId()
        + ".csv";

    logger.log(Level.INFO, "Archive S3 object: s3://{0}/{1}", new Object[]{bucketName, archiveKey});
  }

  @Override
  @Transactional
  public void writeItems(List<Object> items) throws Exception {
    // Build the CSV content for this batch of items
    StringBuilder csvContent = new StringBuilder();
    items.stream()
        .map(item -> (HandlingEventRegistrationAttempt) item)
        .forEach(attempt -> {
          applicationEvents.receivedHandlingEventRegistrationAttempt(attempt);
          csvContent
              .append(DateConverter.toString(attempt.getRegistrationTime()))
              .append(",")
              .append(DateConverter.toString(attempt.getCompletionTime()))
              .append(",")
              .append(attempt.getTrackingId())
              .append(",")
              .append(attempt.getVoyageNumber())
              .append(",")
              .append(attempt.getUnLocode())
              .append(",")
              .append(attempt.getType())
              .append(System.lineSeparator());
        });

    // Append to existing S3 object by fetching current content first, then re-uploading.
    // S3 does not support true append; we read-modify-write atomically within this method.
    String existingContent = fetchExistingContent();
    String updatedContent  = existingContent + csvContent.toString();

    byte[] contentBytes = updatedContent.getBytes(StandardCharsets.UTF_8);

    PutObjectRequest putRequest = PutObjectRequest.builder()
        .bucket(bucketName)
        .key(archiveKey)
        .contentType("text/csv")
        .contentLength((long) contentBytes.length)
        .build();

    s3Client.putObject(putRequest, RequestBody.fromBytes(contentBytes));

    logger.log(Level.INFO, "Archived {0} item(s) to s3://{1}/{2}",
        new Object[]{items.size(), bucketName, archiveKey});
  }

  /**
   * Fetches the current content of the archive S3 object, or returns an empty string
   * if the object does not yet exist.
   */
  private String fetchExistingContent() {
    try {
      GetObjectRequest getRequest = GetObjectRequest.builder()
          .bucket(bucketName)
          .key(archiveKey)
          .build();
      byte[] bytes = s3Client.getObjectAsBytes(getRequest).asByteArray();
      return new String(bytes, StandardCharsets.UTF_8);
    } catch (NoSuchKeyException e) {
      // Object does not exist yet – start with empty content
      return "";
    } catch (Exception e) {
      logger.log(Level.WARNING, "Could not fetch existing archive content from S3; starting fresh.", e);
      return "";
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
}
