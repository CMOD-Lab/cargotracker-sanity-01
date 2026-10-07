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
 * Listens for lines that could not be parsed and writes them to an Amazon S3 "failed" object.
 *
 * <p>Replaces the hard-coded local failed-directory path with S3 object storage so the
 * application can run in cloud environments where the local file system is ephemeral.
 *
 * <p>Required job properties:
 * <ul>
 *   <li>{@code s3_bucket_name}   – name of the S3 bucket for failed-line objects</li>
 *   <li>{@code s3_failed_prefix} – S3 key prefix (folder) for failed objects
 *       (defaults to {@code "failed/"} when not set)</li>
 *   <li>{@code aws_region}       – AWS region of the bucket (defaults to {@code "us-east-1"})</li>
 * </ul>
 */
@Dependent
@Named("LineParseExceptionListener")
public class LineParseExceptionListener implements SkipReadListener {

  private static final String S3_BUCKET_NAME    = "s3_bucket_name";
  private static final String S3_FAILED_PREFIX  = "s3_failed_prefix";
  private static final String AWS_REGION        = "aws_region";
  private static final String DEFAULT_PREFIX    = "failed/";
  private static final String DEFAULT_REGION    = "us-east-1";

  @Inject private Logger logger;

  @Inject private JobContext jobContext;

  @Override
  public void onSkipReadItem(Exception e) throws Exception {
    EventLineParseException parseException = (EventLineParseException) e;

    logger.log(Level.WARNING, "Problem parsing event file line", parseException);

    String bucketName   = resolveBucketName(jobContext.getProperties().getProperty(S3_BUCKET_NAME));
    String failedPrefix = jobContext.getProperties().getProperty(S3_FAILED_PREFIX, DEFAULT_PREFIX);
    String awsRegion    = jobContext.getProperties().getProperty(AWS_REGION, DEFAULT_REGION);

    String failedKey = failedPrefix
        + "failed_"
        + jobContext.getJobName()
        + "_"
        + jobContext.getInstanceId()
        + ".csv";

    try (S3Client s3Client = S3Client.builder()
        .region(Region.of(awsRegion))
        .build()) {

      // Append the failed line to the existing S3 object (read-modify-write).
      String existingContent = fetchExistingContent(s3Client, bucketName, failedKey);
      String updatedContent  = existingContent + parseException.getLine() + System.lineSeparator();

      byte[] contentBytes = updatedContent.getBytes(StandardCharsets.UTF_8);

      PutObjectRequest putRequest = PutObjectRequest.builder()
          .bucket(bucketName)
          .key(failedKey)
          .contentType("text/csv")
          .contentLength((long) contentBytes.length)
          .build();

      s3Client.putObject(putRequest, RequestBody.fromBytes(contentBytes));

      logger.log(Level.INFO, "Wrote failed line to s3://{0}/{1}",
          new Object[]{bucketName, failedKey});
    }
  }

  /**
   * Fetches the current content of the failed S3 object, or returns an empty string
   * if the object does not yet exist.
   */
  private String fetchExistingContent(S3Client s3Client, String bucketName, String key) {
    try {
      GetObjectRequest getRequest = GetObjectRequest.builder()
          .bucket(bucketName)
          .key(key)
          .build();
      byte[] bytes = s3Client.getObjectAsBytes(getRequest).asByteArray();
      return new String(bytes, StandardCharsets.UTF_8);
    } catch (NoSuchKeyException ex) {
      // Object does not exist yet – start with empty content
      return "";
    } catch (Exception ex) {
      logger.log(Level.WARNING, "Could not fetch existing failed content from S3; starting fresh.", ex);
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
