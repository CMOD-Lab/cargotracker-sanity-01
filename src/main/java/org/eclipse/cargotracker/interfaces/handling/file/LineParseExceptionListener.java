package org.eclipse.cargotracker.interfaces.handling.file;

import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;
import jakarta.batch.api.chunk.listener.SkipReadListener;
import jakarta.batch.runtime.context.JobContext;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * Listens for line-parse failures during batch event file processing and records the failed lines
 * to Amazon S3 for durable, cloud-native storage.
 *
 * <p>Replaces the original local file system write operations (FileWriter/PrintWriter to a local
 * failed directory) with Amazon S3 PutObject calls. This ensures data durability and availability
 * in containerized and serverless environments where the local file system is ephemeral.
 *
 * <p>Required job properties:
 * <ul>
 *   <li>{@code s3_bucket_name}  – the S3 bucket to write failed-line objects into</li>
 *   <li>{@code failed_prefix}   – (optional, default {@code "failed/"}) key prefix for failed objects</li>
 *   <li>{@code aws_region}      – (optional, default {@code AWS_REGION} env var or {@code "us-east-1"})</li>
 * </ul>
 */
@Dependent
@Named("LineParseExceptionListener")
public class LineParseExceptionListener implements SkipReadListener {

  private static final String S3_BUCKET_NAME = "s3_bucket_name";
  private static final String S3_FAILED_PREFIX = "failed_prefix";
  private static final String AWS_REGION = "aws_region";

  @Inject private Logger logger;

  @Inject private JobContext jobContext;

  @Override
  public void onSkipReadItem(Exception e) throws Exception {
    String bucketName = jobContext.getProperties().getProperty(S3_BUCKET_NAME);
    String failedPrefix = jobContext.getProperties().getProperty(S3_FAILED_PREFIX, "failed/");
    String awsRegion = jobContext.getProperties().getProperty(AWS_REGION,
        System.getenv().getOrDefault("AWS_REGION", "us-east-1"));

    EventLineParseException parseException = (EventLineParseException) e;

    logger.log(Level.WARNING, "Problem parsing event file line", parseException);

    S3Client s3Client = S3Client.builder()
        .region(Region.of(awsRegion))
        .build();

    String failedKey = failedPrefix + "failed_"
        + jobContext.getJobName()
        + "_"
        + jobContext.getInstanceId()
        + ".csv";

    StringBuilder failedContent = new StringBuilder();

    // Append to existing S3 object content if it exists
    try {
      GetObjectRequest getRequest = GetObjectRequest.builder()
          .bucket(bucketName)
          .key(failedKey)
          .build();
      String existingContent = new String(
          s3Client.getObjectAsBytes(getRequest).asByteArray(), StandardCharsets.UTF_8);
      failedContent.append(existingContent);
    } catch (NoSuchKeyException ex) {
      // Object does not exist yet; start fresh
    }

    failedContent.append(parseException.getLine()).append(System.lineSeparator());

    byte[] contentBytes = failedContent.toString().getBytes(StandardCharsets.UTF_8);
    PutObjectRequest putRequest = PutObjectRequest.builder()
        .bucket(bucketName)
        .key(failedKey)
        .contentType("text/csv")
        .contentLength((long) contentBytes.length)
        .build();
    s3Client.putObject(putRequest, RequestBody.fromBytes(contentBytes));
  }
}
