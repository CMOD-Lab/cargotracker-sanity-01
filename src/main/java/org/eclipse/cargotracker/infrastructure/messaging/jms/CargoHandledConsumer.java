package org.eclipse.cargotracker.infrastructure.messaging.jms;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import jakarta.enterprise.concurrent.ManagedScheduledExecutorService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.cargotracker.application.CargoInspectionService;
import org.eclipse.cargotracker.domain.model.cargo.TrackingId;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

/**
 * Consumes Amazon SQS messages and delegates notification of handled cargo to the inspection
 * service.
 *
 * <p>Replaces the JMS {@code @MessageDriven} / {@code MessageListener} pattern with a
 * cloud-native Amazon SQS polling consumer using AWS SDK for Java v2. The queue URL is
 * resolved from the environment variable {@code CARGO_HANDLED_QUEUE_URL}.
 */
@ApplicationScoped
public class CargoHandledConsumer {

  /** Environment variable that holds the SQS queue URL for cargo-handled events. */
  private static final String QUEUE_URL_ENV = "CARGO_HANDLED_QUEUE_URL";

  /** Maximum number of messages to retrieve per poll (SQS maximum is 10). */
  private static final int MAX_MESSAGES = 10;

  /** Long-poll wait time in seconds (reduces empty-response API calls). */
  private static final int WAIT_TIME_SECONDS = 20;

  @Inject private Logger logger;

  @Inject private CargoInspectionService cargoInspectionService;

  @Inject private SqsClient sqsClient;

  @Resource
  private ManagedScheduledExecutorService scheduledExecutor;

  private String queueUrl;

  private volatile boolean running = false;

  private java.util.concurrent.ScheduledFuture<?> pollingTask;

  @PostConstruct
  public void init() {
    queueUrl = System.getenv(QUEUE_URL_ENV);
    if (queueUrl == null || queueUrl.isBlank()) {
      logger.log(
          Level.WARNING,
          "Environment variable {0} is not set; CargoHandledConsumer will not poll SQS.",
          QUEUE_URL_ENV);
      return;
    }
    running = true;
    pollingTask =
        scheduledExecutor.scheduleWithFixedDelay(
            this::pollMessages,
            0,
            1,
            java.util.concurrent.TimeUnit.SECONDS);
    logger.log(Level.INFO, "CargoHandledConsumer started polling SQS queue: {0}", queueUrl);
  }

  @PreDestroy
  public void shutdown() {
    running = false;
    if (pollingTask != null) {
      pollingTask.cancel(false);
    }
    logger.log(Level.INFO, "CargoHandledConsumer stopped.");
  }

  /**
   * Polls the Amazon SQS queue for cargo-handled messages and processes each one.
   * Each successfully processed message is deleted from the queue.
   */
  void pollMessages() {
    if (!running || queueUrl == null) {
      return;
    }
    try {
      ReceiveMessageRequest receiveRequest =
          ReceiveMessageRequest.builder()
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
      logger.log(Level.SEVERE, "Error polling SQS queue for cargo-handled messages", e);
    }
  }

  /**
   * Processes a single SQS message: extracts the tracking ID, invokes the inspection service,
   * and deletes the message from the queue upon success.
   *
   * @param message the SQS message to process
   */
  private void processMessage(Message message) {
    try {
      String trackingIdString = message.body();
      cargoInspectionService.inspectCargo(new TrackingId(trackingIdString));

      // Delete the message from the queue after successful processing
      DeleteMessageRequest deleteRequest =
          DeleteMessageRequest.builder()
              .queueUrl(queueUrl)
              .receiptHandle(message.receiptHandle())
              .build();
      sqsClient.deleteMessage(deleteRequest);

      logger.log(Level.INFO, "Successfully processed cargo-handled event for tracking ID: {0}",
          trackingIdString);
    } catch (Exception e) {
      logger.log(Level.SEVERE, "Error processing SQS message for cargo-handled event", e);
      // Message will become visible again after the visibility timeout expires,
      // allowing for automatic retry by SQS.
    }
  }
}
