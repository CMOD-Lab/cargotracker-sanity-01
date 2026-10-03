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
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Dependent
@Named("LineParseExceptionListener")
public class LineParseExceptionListener implements SkipReadListener {

  private static final String FAILED_BUCKET = "failed_directory";

  @Inject private Logger logger;

  @Inject private JobContext jobContext;

  @Override
  public void onSkipReadItem(Exception e) throws Exception {
    String failedBucket = jobContext.getProperties().getProperty(FAILED_BUCKET);
    String failedKey = "failed_"
        + jobContext.getJobName()
        + "_"
        + jobContext.getInstanceId()
        + ".csv";

    EventLineParseException parseException = (EventLineParseException) e;

    logger.log(Level.WARNING, "Problem parsing event file line", parseException);

    S3Client s3Client = S3Client.builder().build();

    // Append the failed line to the S3 object by reading existing content and re-uploading
    String existingContent = "";
    try {
      HeadObjectRequest headRequest = HeadObjectRequest.builder()
          .bucket(failedBucket)
          .key(failedKey)
          .build();
      s3Client.headObject(headRequest);

      // Object exists — read existing content
      GetObjectRequest getRequest = GetObjectRequest.builder()
          .bucket(failedBucket)
          .key(failedKey)
          .build();
      existingContent = new String(s3Client.getObjectAsBytes(getRequest).asByteArray(), StandardCharsets.UTF_8);
    } catch (NoSuchKeyException ex) {
      // Object does not exist yet; start fresh
    }

    String updatedContent = existingContent + parseException.getLine() + "\n";
    byte[] contentBytes = updatedContent.getBytes(StandardCharsets.UTF_8);

    PutObjectRequest putRequest = PutObjectRequest.builder()
        .bucket(failedBucket)
        .key(failedKey)
        .contentType("text/csv")
        .contentLength((long) contentBytes.length)
        .build();

    s3Client.putObject(putRequest, RequestBody.fromBytes(contentBytes));
  }
}
