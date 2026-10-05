package org.eclipse.cargotracker.infrastructure.messaging.jms;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.cargotracker.application.CargoInspectionService;
import org.eclipse.cargotracker.domain.model.cargo.TrackingId;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import software.amazon.awssdk.services.sqs.model.SqsException;

/**
 * Consumes Amazon SQS messages and delegates notification of handled cargo to the inspection
 * service.
 *
 * <p>Replaces the JMS {@code @MessageDriven} MDB pattern with a cloud-native Amazon SQS
 * polling consumer, eliminating the dependency on traditional message brokers (ActiveMQ,
 * IBM MQ, WebLogic JMS) and leveraging the fully-managed AWS SQS service instead.
 */
@ApplicationScoped
public class CargoHandledConsumer {

  /** Environment variable name for the SQS queue URL. */
  private static final String QUEUE_URL_ENV = "CARGO_HANDLED_QUEUE_URL";

  /** Maximum number of messages to retrieve per poll (SQS max is 10). */
  private static final int MAX_MESSAGES = 10;

  /** SQS long-poll wait time in seconds (reduces empty-response API calls). */
  private static final int WAIT_TIME_SECONDS = 20;

  @Inject private Logger logger;

  @Inject private CargoInspectionService cargoInspectionService;

  private SqsClient sqsClient;
  private String queueUrl;

  /**
   * Initialises the Amazon SQS client and resolves the queue URL from the environment.
   *
   * <p>The AWS region is read from the {@code AWS_REGION} environment variable (standard
   * AWS SDK convention); it falls back to {@code us-east-1} when the variable is absent so
   * that local/test environments continue to work without additional configuration.
   */
  @PostConstruct
  public void init() {
    String region = System.getenv("AWS_REGION");
    if (region == null || region.isBlank()) {
      region = "us-east-1";
    }

    sqsClient = SqsClient.builder()
        .region(Region.of(region))
        .build();

    queueUrl = System.getenv(QUEUE_URL_ENV);
    if (queueUrl == null || queueUrl.isBlank()) {
      logger.log(Level.WARNING,
          "Environment variable {0} is not set; CargoHandledConsumer will not poll SQS.",
          QUEUE_URL_ENV);
    } else {
      logger.log(Level.INFO,
          "CargoHandledConsumer initialised with SQS queue URL: {0}", queueUrl);
    }
  }

  /**
   * Polls the Amazon SQS {@code CargoHandledQueue} for messages and processes each one.
   *
   * <p>Each message body is expected to contain the plain-text tracking ID string that was
   * originally sent via {@code JmsApplicationEvents#cargoWasHandled}. After successful
   * processing the message is deleted from the queue to prevent redelivery.
   *
   * <p>This method is intended to be invoked periodically (e.g. via a scheduled executor or
   * a Jakarta EE {@code @Schedule} timer) to replace the push-based MDB delivery model.
   */
  public void pollAndProcess() {
    if (queueUrl == null || queueUrl.isBlank()) {
      logger.log(Level.WARNING,
          "SQS queue URL not configured; skipping poll for CargoHandledConsumer.");
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
    } catch (SqsException e) {
      logger.log(Level.SEVERE, "Error polling SQS queue for CargoHandledConsumer", e);
    }
  }

  /**
   * Processes a single SQS message: extracts the tracking ID, invokes cargo inspection,
   * and deletes the message from the queue on success.
   *
   * @param message the SQS {@link Message} to process
   */
  private void processMessage(Message message) {
    String trackingIdString = message.body();
    try {
      logger.log(Level.INFO, "Processing cargo handled event for tracking ID: {0}",
          trackingIdString);

      cargoInspectionService.inspectCargo(new TrackingId(trackingIdString));

      // Delete the message from the queue after successful processing
      DeleteMessageRequest deleteRequest = DeleteMessageRequest.builder()
          .queueUrl(queueUrl)
          .receiptHandle(message.receiptHandle())
          .build();
      sqsClient.deleteMessage(deleteRequest);

      logger.log(Level.INFO,
          "Successfully processed and deleted SQS message for tracking ID: {0}",
          trackingIdString);
    } catch (Exception e) {
      logger.log(Level.SEVERE,
          "Error processing SQS message for tracking ID: " + trackingIdString, e);
      // Message will become visible again after the visibility timeout expires,
      // allowing for automatic retry. After the configured maximum receive count
      // the message will be moved to the dead-letter queue (if configured).
    }
  }

  /** Closes the SQS client when the bean is destroyed to release SDK resources. */
  @PreDestroy
  public void cleanup() {
    if (sqsClient != null) {
      sqsClient.close();
      logger.log(Level.INFO, "SQS client closed for CargoHandledConsumer.");
    }
  }
}
