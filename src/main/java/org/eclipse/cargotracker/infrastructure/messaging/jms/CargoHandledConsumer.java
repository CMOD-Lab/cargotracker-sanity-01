package org.eclipse.cargotracker.infrastructure.messaging.jms;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;
import jakarta.ejb.Timeout;
import jakarta.ejb.Timer;
import jakarta.ejb.TimerConfig;
import jakarta.ejb.TimerService;
import jakarta.inject.Inject;
import org.eclipse.cargotracker.application.CargoInspectionService;
import org.eclipse.cargotracker.domain.model.cargo.TrackingId;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

/**
 * Consumes Amazon SQS messages and delegates notification of handled cargo to the inspection
 * service.
 *
 * <p>Replaces the JMS {@code @MessageDriven} bean with a cloud-native AWS SQS polling consumer
 * using the AWS SDK for Java v2. The queue URL is resolved from the environment variable
 * {@code CARGO_HANDLED_QUEUE_URL}, enabling externalized configuration per 12-factor app
 * principles.
 */
@Singleton
@Startup
public class CargoHandledConsumer {

  /** Environment variable that holds the SQS queue URL for the CargoHandled queue. */
  private static final String QUEUE_URL_ENV = "CARGO_HANDLED_QUEUE_URL";

  /** Polling interval in milliseconds (5 seconds). */
  private static final long POLL_INTERVAL_MS = 5_000L;

  /** Maximum number of messages to retrieve per poll (SQS max is 10). */
  private static final int MAX_MESSAGES = 10;

  /** SQS long-poll wait time in seconds (reduces empty-response calls). */
  private static final int WAIT_TIME_SECONDS = 20;

  @Inject private Logger logger;

  @Inject private CargoInspectionService cargoInspectionService;

  @Resource private TimerService timerService;

  private SqsClient sqsClient;
  private String queueUrl;
  private Timer pollingTimer;

  @PostConstruct
  public void init() {
    queueUrl = System.getenv(QUEUE_URL_ENV);
    if (queueUrl == null || queueUrl.isBlank()) {
      logger.log(
          Level.WARNING,
          "Environment variable {0} is not set. CargoHandledConsumer will not poll SQS.",
          QUEUE_URL_ENV);
      return;
    }

    String awsRegion = System.getenv("AWS_REGION");
    Region region = (awsRegion != null && !awsRegion.isBlank())
        ? Region.of(awsRegion)
        : Region.US_EAST_1;

    sqsClient = SqsClient.builder()
        .region(region)
        .build();

    // Schedule a repeating timer to poll SQS at a fixed interval.
    TimerConfig timerConfig = new TimerConfig("CargoHandledConsumer-SQS-Poll", false);
    pollingTimer = timerService.createIntervalTimer(POLL_INTERVAL_MS, POLL_INTERVAL_MS, timerConfig);

    logger.log(Level.INFO,
        "CargoHandledConsumer initialised. Polling SQS queue: {0} every {1} ms.",
        new Object[]{queueUrl, POLL_INTERVAL_MS});
  }

  @PreDestroy
  public void shutdown() {
    if (pollingTimer != null) {
      try {
        pollingTimer.cancel();
      } catch (Exception e) {
        logger.log(Level.WARNING, "Error cancelling SQS polling timer.", e);
      }
    }
    if (sqsClient != null) {
      sqsClient.close();
    }
  }

  /**
   * EJB timer callback – polls the SQS queue and processes any available messages.
   *
   * @param timer the EJB timer that triggered this callback
   */
  @Timeout
  public void pollQueue(Timer timer) {
    if (sqsClient == null || queueUrl == null) {
      return;
    }

    try {
      ReceiveMessageRequest receiveRequest = ReceiveMessageRequest.builder()
          .queueUrl(queueUrl)
          .maxNumberOfMessages(MAX_MESSAGES)
          .waitTimeSeconds(WAIT_TIME_SECONDS)
          .build();

      ReceiveMessageResponse response = sqsClient.receiveMessage(receiveRequest);
      List<Message> messages = response.messages();

      for (Message message : messages) {
        processMessage(message);
      }
    } catch (Exception e) {
      logger.log(Level.SEVERE, "Error polling SQS queue for CargoHandled messages.", e);
    }
  }

  /**
   * Processes a single SQS message: extracts the tracking ID, invokes cargo inspection, then
   * deletes the message from the queue to acknowledge successful processing.
   *
   * @param message the SQS message to process
   */
  private void processMessage(Message message) {
    String trackingIdString = message.body();
    try {
      cargoInspectionService.inspectCargo(new TrackingId(trackingIdString));

      // Delete the message from SQS after successful processing (acknowledgement).
      DeleteMessageRequest deleteRequest = DeleteMessageRequest.builder()
          .queueUrl(queueUrl)
          .receiptHandle(message.receiptHandle())
          .build();
      sqsClient.deleteMessage(deleteRequest);

      logger.log(Level.INFO, "Successfully processed CargoHandled SQS message for tracking ID: {0}",
          trackingIdString);
    } catch (Exception e) {
      // Do NOT delete the message – it will become visible again after the visibility timeout
      // and be retried, or eventually moved to a Dead Letter Queue (DLQ) if configured.
      logger.log(Level.SEVERE,
          "Error processing CargoHandled SQS message for tracking ID: " + trackingIdString, e);
    }
  }
}
