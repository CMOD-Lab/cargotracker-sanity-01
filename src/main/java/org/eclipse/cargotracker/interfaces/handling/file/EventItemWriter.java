package org.eclipse.cargotracker.interfaces.handling.file;

import java.io.ByteArrayInputStream;
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
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * Writes processed handling event records to an Amazon S3 bucket as a CSV archive object,
 * replacing the previous local file-system archive directory dependency.
 *
 * <p>The S3 bucket name is resolved from the job property "archive_directory"
 * (treated as the S3 bucket name). The AWS region is read from the environment
 * variable AWS_REGION (defaulting to us-east-1).
 */
@Dependent
@Named("EventItemWriter")
public class EventItemWriter extends AbstractItemWriter {

  /** Job property key whose value is the S3 bucket name for archived event files. */
  private static final String ARCHIVE_DIRECTORY = "archive_directory";

  @Inject private JobContext jobContext;
  @Inject private ApplicationEvents applicationEvents;
  @Inject private Logger logger;

  private S3Client s3Client;
  private String s3BucketName;

  @Override
  public void open(Serializable checkpoint) throws Exception {
    // Resolve S3 bucket name from job property (replaces hard-coded local directory path)
    s3BucketName = jobContext.getProperties().getProperty(ARCHIVE_DIRECTORY);

    String awsRegion = System.getenv("AWS_REGION");
    if (awsRegion == null || awsRegion.isEmpty()) {
      awsRegion = "us-east-1";
    }

    s3Client = S3Client.builder()
        .region(Region.of(awsRegion))
        .build();

    logger.log(Level.INFO, "EventItemWriter opened; archive S3 bucket: {0}", s3BucketName);
  }

  @Override
  @Transactional
  public void writeItems(List<Object> items) throws Exception {
    StringBuilder csvContent = new StringBuilder();

    items.stream()
        .map(item -> (HandlingEventRegistrationAttempt) item)
        .forEach(
            attempt -> {
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

    // Build a unique S3 object key for this job execution's archive chunk
    String s3Key = "archive_"
        + jobContext.getJobName()
        + "_"
        + jobContext.getInstanceId()
        + "_"
        + System.currentTimeMillis()
        + ".csv";

    byte[] contentBytes = csvContent.toString().getBytes(StandardCharsets.UTF_8);

    PutObjectRequest putRequest = PutObjectRequest.builder()
        .bucket(s3BucketName)
        .key(s3Key)
        .contentType("text/csv")
        .contentLength((long) contentBytes.length)
        .build();

    s3Client.putObject(putRequest, RequestBody.fromBytes(contentBytes));

    logger.log(Level.INFO, "Archived {0} item(s) to s3://{1}/{2}",
        new Object[]{items.size(), s3BucketName, s3Key});
  }
}
