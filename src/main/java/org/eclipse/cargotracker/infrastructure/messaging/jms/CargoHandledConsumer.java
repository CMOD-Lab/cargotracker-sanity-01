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
 * <p>This replaces the JMS {@code @MessageDriven} EJB with a cloud-native Amazon SQS polling
 * consumer using the AWS SDK for Java v2. The queue URL is resolved from the environment variable
 * {@code CARGO_HANDLED_QUEUE_URL}.
 */
@Singleton
@Startup
public class CargoHandledConsumer {

  private static final String QUEUE_URL_ENV = "CARGO_HANDLED_QUEUE_URL";
  private static final int POLL_INTERVAL_MS = 5000;
  private static final int MAX_MESSAGES = 10;
  private static final int WAIT_TIME_SECONDS = 20; // long-polling

  @Inject private Logger logger;

  @Inject private CargoInspectionService cargoInspectionService;

  @Resource private TimerService timerService;

  private SqsClient sqsClient;
  private String queueUrl;

  @PostConstruct
  public void init() {
    String region = System.getenv("AWS_REGION");
    if (region == null || region.isEmpty()) {
      region = "us-east-1";
    }
    sqsClient = SqsClient.builder().region(Region.of(region)).build();

    queueUrl = System.getenv(QUEUE_URL_ENV);
    if (queueUrl == null || queueUrl.isEmpty()) {
      logger.log(
          Level.WARNING,
          "Environment variable {0} is not set; CargoHandledConsumer will not poll.",
          QUEUE_URL_ENV);
      return;
    }

    // Schedule a repeating interval timer to poll SQS
    TimerConfig config = new TimerConfig("CargoHandledConsumer-poll", false);
    timerService.createIntervalTimer(0, POLL_INTERVAL_MS, config);
    logger.log(Level.INFO, "CargoHandledConsumer started polling SQS queue: {0}", queueUrl);
  }

  @Timeout
  public void pollQueue(Timer timer) {
    if (queueUrl == null || queueUrl.isEmpty()) {
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
      logger.log(Level.SEVERE, "Error polling SQS queue for CargoHandled messages", e);
    }
  }

  private void processMessage(Message message) {
    String trackingIdString = message.body();
    try {
      cargoInspectionService.inspectCargo(new TrackingId(trackingIdString));

      // Delete the message from the queue after successful processing
      DeleteMessageRequest deleteRequest =
          DeleteMessageRequest.builder()
              .queueUrl(queueUrl)
              .receiptHandle(message.receiptHandle())
              .build();
      sqsClient.deleteMessage(deleteRequest);

      logger.log(Level.INFO, "Successfully processed CargoHandled SQS message for tracking ID: {0}", trackingIdString);
    } catch (Exception e) {
      logger.log(
          Level.SEVERE,
          "Error processing CargoHandled SQS message for tracking ID: " + trackingIdString,
          e);
      // Message will become visible again after the visibility timeout for retry
    }
  }

  @PreDestroy
  public void cleanup() {
    if (sqsClient != null) {
      sqsClient.close();
    }
  }
}
