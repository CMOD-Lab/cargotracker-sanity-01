package org.eclipse.cargotracker.interfaces.handling.file;

import java.io.ByteArrayInputStream;
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
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Dependent
@Named("EventItemWriter")
public class EventItemWriter extends AbstractItemWriter {

  private static final String ARCHIVE_BUCKET = "archive_directory";

  @Inject private JobContext jobContext;
  @Inject private ApplicationEvents applicationEvents;

  private S3Client s3Client;

  @Override
  public void open(Serializable checkpoint) throws Exception {
    s3Client = S3Client.builder().build();
    // S3 bucket is managed externally; no directory creation needed
    String archiveBucket = jobContext.getProperties().getProperty(ARCHIVE_BUCKET);
    if (archiveBucket == null || archiveBucket.isEmpty()) {
      throw new IllegalStateException("Archive S3 bucket name must be configured via '" + ARCHIVE_BUCKET + "' job property");
    }
  }

  @Override
  @Transactional
  public void writeItems(List<Object> items) throws Exception {
    String archiveBucket = jobContext.getProperties().getProperty(ARCHIVE_BUCKET);
    String archiveKey = "archive_"
        + jobContext.getJobName()
        + "_"
        + jobContext.getInstanceId()
        + ".csv";

    // Build the CSV content for this batch of items
    StringBuilder csvContent = new StringBuilder();

    items
        .stream()
        .map(item -> (HandlingEventRegistrationAttempt) item)
        .forEach(
            attempt -> {
              applicationEvents.receivedHandlingEventRegistrationAttempt(attempt);
              csvContent.append(
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
                      + "\n");
            });

    // Append to existing S3 object by reading existing content and re-uploading
    String existingContent = "";
    try {
      HeadObjectRequest headRequest = HeadObjectRequest.builder()
          .bucket(archiveBucket)
          .key(archiveKey)
          .build();
      s3Client.headObject(headRequest);

      // Object exists — read existing content
      GetObjectRequest getRequest = GetObjectRequest.builder()
          .bucket(archiveBucket)
          .key(archiveKey)
          .build();
      existingContent = new String(s3Client.getObjectAsBytes(getRequest).asByteArray(), StandardCharsets.UTF_8);
    } catch (NoSuchKeyException e) {
      // Object does not exist yet; start fresh
    }

    String combinedContent = existingContent + csvContent.toString();
    byte[] contentBytes = combinedContent.getBytes(StandardCharsets.UTF_8);

    PutObjectRequest putRequest = PutObjectRequest.builder()
        .bucket(archiveBucket)
        .key(archiveKey)
        .contentType("text/csv")
        .contentLength((long) contentBytes.length)
        .build();

    s3Client.putObject(putRequest, RequestBody.fromInputStream(
        new ByteArrayInputStream(contentBytes), contentBytes.length));
  }
}
