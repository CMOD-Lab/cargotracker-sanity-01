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
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;

@Dependent
@Named("EventItemReader")
public class EventItemReader extends AbstractItemReader {

  private static final String UPLOAD_BUCKET = "upload_directory";

  @Inject private Logger logger;

  @Inject private JobContext jobContext;
  private EventFilesCheckpoint checkpoint;
  private BufferedReader currentReader;
  private S3Client s3Client;

  @Override
  public void open(Serializable checkpoint) throws Exception {
    s3Client = S3Client.builder().build();
    String uploadBucket = jobContext.getProperties().getProperty(UPLOAD_BUCKET);

    if (checkpoint == null) {
      this.checkpoint = new EventFilesCheckpoint();
      logger.log(Level.INFO, "Scanning upload bucket: {0}", uploadBucket);

      ListObjectsV2Request listRequest = ListObjectsV2Request.builder()
          .bucket(uploadBucket)
          .build();
      ListObjectsV2Response listResponse = s3Client.listObjectsV2(listRequest);

      if (listResponse.contents().isEmpty()) {
        logger.log(Level.INFO, "Upload bucket is empty, no files to process");
      } else {
        List<String> keys = new ArrayList<>();
        for (S3Object s3Object : listResponse.contents()) {
          keys.add(s3Object.key());
        }
        this.checkpoint.setFiles(keys);
      }
    } else {
      logger.log(Level.INFO, "Starting from previous checkpoint");
      this.checkpoint = (EventFilesCheckpoint) checkpoint;
    }

    String currentKey = this.checkpoint.currentFile();

    if (currentKey == null) {
      logger.log(Level.INFO, "No files to process");
      currentReader = null;
    } else {
      openS3Object(uploadBucket, currentKey);
      logger.log(Level.INFO, "Processing S3 object: {0}", currentKey);
      skipToCheckpoint();
    }
  }

  private void openS3Object(String bucket, String key) throws Exception {
    GetObjectRequest getRequest = GetObjectRequest.builder()
        .bucket(bucket)
        .key(key)
        .build();
    ResponseInputStream<GetObjectResponse> s3Stream = s3Client.getObject(getRequest);
    currentReader = new BufferedReader(new InputStreamReader(s3Stream));
  }

  private void skipToCheckpoint() throws Exception {
    long linesToSkip = this.checkpoint.getFilePointer();
    for (long i = 0; i < linesToSkip; i++) {
      if (currentReader.readLine() == null) {
        break;
      }
    }
  }

  @Override
  public Object readItem() throws Exception {
    if (currentReader != null) {
      String line = currentReader.readLine();

      if (line != null) {
        this.checkpoint.incrementFilePointer();
        return parseLine(line);
      } else {
        String currentKey = this.checkpoint.currentFile();
        String uploadBucket = jobContext.getProperties().getProperty(UPLOAD_BUCKET);
        logger.log(Level.INFO, "Finished processing S3 object, deleting: {0}", currentKey);
        currentReader.close();
        currentReader = null;

        // Delete the processed object from S3
        DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
            .bucket(uploadBucket)
            .key(currentKey)
            .build();
        s3Client.deleteObject(deleteRequest);

        String nextKey = this.checkpoint.nextFile();

        if (nextKey == null) {
          logger.log(Level.INFO, "No more files to process");
          return null;
        } else {
          openS3Object(uploadBucket, nextKey);
          logger.log(Level.INFO, "Processing S3 object: {0}", nextKey);
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
