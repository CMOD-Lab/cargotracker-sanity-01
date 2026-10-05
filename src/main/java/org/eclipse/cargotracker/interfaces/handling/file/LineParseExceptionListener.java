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
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * Listens for line-parse failures during batch reading and writes the failed line to an
 * Amazon S3 bucket instead of a local "failed" directory on the host file system.
 *
 * <p>The S3 bucket name is resolved from the job property "failed_directory"
 * (treated as the S3 bucket name). The AWS region is read from the environment
 * variable AWS_REGION (defaulting to us-east-1).
 */
@Dependent
@Named("LineParseExceptionListener")
public class LineParseExceptionListener implements SkipReadListener {

  /** Job property key whose value is the S3 bucket name for failed-parse records. */
  private static final String FAILED_DIRECTORY = "failed_directory";

  @Inject private Logger logger;

  @Inject private JobContext jobContext;

  @Override
  public void onSkipReadItem(Exception e) throws Exception {
    // Resolve S3 bucket name from job property (replaces hard-coded local directory path)
    String s3BucketName = jobContext.getProperties().getProperty(FAILED_DIRECTORY);

    String awsRegion = System.getenv("AWS_REGION");
    if (awsRegion == null || awsRegion.isEmpty()) {
      awsRegion = "us-east-1";
    }

    EventLineParseException parseException = (EventLineParseException) e;

    logger.log(Level.WARNING, "Problem parsing event file line", parseException);

    // Build a unique S3 object key for this failed line
    String s3Key = "failed_"
        + jobContext.getJobName()
        + "_"
        + jobContext.getInstanceId()
        + "_"
        + System.currentTimeMillis()
        + ".csv";

    byte[] contentBytes = (parseException.getLine() + System.lineSeparator())
        .getBytes(StandardCharsets.UTF_8);

    try {
      S3Client s3Client = S3Client.builder()
          .region(Region.of(awsRegion))
          .build();

      PutObjectRequest putRequest = PutObjectRequest.builder()
          .bucket(s3BucketName)
          .key(s3Key)
          .contentType("text/csv")
          .contentLength((long) contentBytes.length)
          .build();

      s3Client.putObject(putRequest, RequestBody.fromBytes(contentBytes));

      logger.log(Level.INFO, "Wrote failed line to s3://{0}/{1}",
          new Object[]{s3BucketName, s3Key});
    } catch (Exception ex) {
      logger.log(Level.SEVERE, "Could not write failed line to S3 bucket: " + s3BucketName, ex);
    }
  }
}
