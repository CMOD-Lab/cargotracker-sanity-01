package org.eclipse.cargotracker.interfaces.handling.file;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.List;
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
 * Writes handled cargo events to Amazon S3 for durable, cloud-native storage.
 *
 * <p>Replaces the original local file system write operations (FileWriter/PrintWriter to a local
 * archive directory) with Amazon S3 PutObject calls. This ensures data durability and availability
 * in containerized and serverless environments where the local file system is ephemeral.
 *
 * <p>Required job properties:
 * <ul>
 *   <li>{@code s3_bucket_name}  – the S3 bucket to write archive objects into</li>
 *   <li>{@code archive_prefix}  – (optional, default {@code "archive/"}) key prefix for archive objects</li>
 *   <li>{@code aws_region}      – (optional, default {@code AWS_REGION} env var or {@code "us-east-1"})</li>
 * </ul>
 */
@Dependent
@Named("EventItemWriter")
public class EventItemWriter extends AbstractItemWriter {

  private static final String S3_BUCKET_NAME = "s3_bucket_name";
  private static final String S3_ARCHIVE_PREFIX = "archive_prefix";
  private static final String AWS_REGION = "aws_region";

  @Inject private JobContext jobContext;
  @Inject private ApplicationEvents applicationEvents;

  private S3Client s3Client;
  private String bucketName;
  private String archiveKey;

  @Override
  public void open(Serializable checkpoint) throws Exception {
    String awsRegion = jobContext.getProperties().getProperty(AWS_REGION,
        System.getenv().getOrDefault("AWS_REGION", "us-east-1"));
    bucketName = jobContext.getProperties().getProperty(S3_BUCKET_NAME);
    String archivePrefix = jobContext.getProperties().getProperty(S3_ARCHIVE_PREFIX, "archive/");

    s3Client = S3Client.builder()
        .region(Region.of(awsRegion))
        .build();

    archiveKey = archivePrefix + "archive_"
        + jobContext.getJobName()
        + "_"
        + jobContext.getInstanceId()
        + ".csv";
  }

  @Override
  @Transactional
  public void writeItems(List<Object> items) throws Exception {
    StringBuilder archiveContent = new StringBuilder();

    // Append to existing S3 object content if it exists
    try {
      GetObjectRequest getRequest = GetObjectRequest.builder()
          .bucket(bucketName)
          .key(archiveKey)
          .build();
      String existingContent = new String(
          s3Client.getObjectAsBytes(getRequest).asByteArray(), StandardCharsets.UTF_8);
      archiveContent.append(existingContent);
    } catch (NoSuchKeyException e) {
      // Object does not exist yet; start fresh
    }

    items.stream()
        .map(item -> (HandlingEventRegistrationAttempt) item)
        .forEach(attempt -> {
          applicationEvents.receivedHandlingEventRegistrationAttempt(attempt);
          archiveContent.append(
              DateConverter.toString(attempt.getRegistrationTime())
                  + ","
                  + DateConverter.toString(attempt.getCompletionTime())
                  + ","
                  + attempt.getTrackingId()
                  + ","
                  + attempt.getVoyageNumber()
                  + ","
                  + attempt.getUnLocode()
                  + ","
                  + attempt.getType()
                  + System.lineSeparator());
        });

    byte[] contentBytes = archiveContent.toString().getBytes(StandardCharsets.UTF_8);
    PutObjectRequest putRequest = PutObjectRequest.builder()
        .bucket(bucketName)
        .key(archiveKey)
        .contentType("text/csv")
        .contentLength((long) contentBytes.length)
        .build();
    s3Client.putObject(putRequest, RequestBody.fromBytes(contentBytes));
  }
}
