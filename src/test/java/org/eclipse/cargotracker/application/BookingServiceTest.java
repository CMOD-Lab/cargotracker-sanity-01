package org.eclipse.cargotracker.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import java.util.Random;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.eclipse.cargotracker.application.internal.DefaultBookingService;
import org.eclipse.cargotracker.application.util.DateConverter;
import org.eclipse.cargotracker.application.util.RestConfiguration;
import org.eclipse.cargotracker.domain.model.cargo.Cargo;
import org.eclipse.cargotracker.domain.model.cargo.CargoRepository;
import org.eclipse.cargotracker.domain.model.cargo.Delivery;
import org.eclipse.cargotracker.domain.model.cargo.HandlingActivity;
import org.eclipse.cargotracker.domain.model.cargo.Itinerary;
import org.eclipse.cargotracker.domain.model.cargo.Leg;
import org.eclipse.cargotracker.domain.model.cargo.RouteSpecification;
import org.eclipse.cargotracker.domain.model.cargo.RoutingStatus;
import org.eclipse.cargotracker.domain.model.cargo.TrackingId;
import org.eclipse.cargotracker.domain.model.cargo.TransportStatus;
import org.eclipse.cargotracker.domain.model.handling.CannotCreateHandlingEventException;
import org.eclipse.cargotracker.domain.model.handling.HandlingEvent;
import org.eclipse.cargotracker.domain.model.handling.HandlingEventFactory;
import org.eclipse.cargotracker.domain.model.handling.HandlingEventRepository;
import org.eclipse.cargotracker.domain.model.handling.HandlingHistory;
import org.eclipse.cargotracker.domain.model.handling.UnknownCargoException;
import org.eclipse.cargotracker.domain.model.handling.UnknownLocationException;
import org.eclipse.cargotracker.domain.model.handling.UnknownVoyageException;
import org.eclipse.cargotracker.domain.model.location.Location;
import org.eclipse.cargotracker.domain.model.location.LocationRepository;
import org.eclipse.cargotracker.domain.model.location.SampleLocations;
import org.eclipse.cargotracker.domain.model.location.UnLocode;
import org.eclipse.cargotracker.domain.model.voyage.CarrierMovement;
import org.eclipse.cargotracker.domain.model.voyage.SampleVoyages;
import org.eclipse.cargotracker.domain.model.voyage.Schedule;
import org.eclipse.cargotracker.domain.model.voyage.Voyage;
import org.eclipse.cargotracker.domain.model.voyage.VoyageNumber;
import org.eclipse.cargotracker.domain.model.voyage.VoyageRepository;
import org.eclipse.cargotracker.domain.service.RoutingService;
import org.eclipse.cargotracker.domain.shared.AbstractSpecification;
import org.eclipse.cargotracker.domain.shared.AndSpecification;
import org.eclipse.cargotracker.domain.shared.DomainObjectUtils;
import org.eclipse.cargotracker.domain.shared.NotSpecification;
import org.eclipse.cargotracker.domain.shared.OrSpecification;
import org.eclipse.cargotracker.domain.shared.Specification;
import org.eclipse.cargotracker.infrastructure.logging.LoggerProducer;
import org.eclipse.cargotracker.infrastructure.persistence.jpa.JpaCargoRepository;
import org.eclipse.cargotracker.infrastructure.persistence.jpa.JpaHandlingEventRepository;
import org.eclipse.cargotracker.infrastructure.persistence.jpa.JpaLocationRepository;
import org.eclipse.cargotracker.infrastructure.persistence.jpa.JpaVoyageRepository;
import org.eclipse.cargotracker.infrastructure.routing.ExternalRoutingService;
import org.eclipse.pathfinder.api.GraphTraversalService;
import org.eclipse.pathfinder.api.TransitEdge;
import org.eclipse.pathfinder.api.TransitPath;
import org.eclipse.pathfinder.internal.GraphDao;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit5.ArquillianExtension;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Application layer integration test covering a number of otherwise fairly trivial components that
 * largely do not warrant their own tests.
 *
 * <p>Cloud Readiness (cr-java-0066): The previously static mutable fields
 * ({@code trackingId}, {@code candidates}, {@code deadline}, {@code assigned}) have been replaced
 * with instance-level fields whose values are persisted to and retrieved from Amazon ElastiCache
 * for Redis between ordered test methods. This eliminates per-JVM static state divergence in
 * multi-instance cloud deployments and ensures that all test instances share a consistent view of
 * the test execution state.
 *
 * <p>Redis connection parameters are supplied through environment variables:
 * <ul>
 *   <li>{@code REDIS_HOST} – ElastiCache primary endpoint (default: {@code localhost})</li>
 *   <li>{@code REDIS_PORT} – TCP port (default: {@code 6379})</li>
 * </ul>
 *
 * <p>When Redis is unavailable the fields fall back to in-process instance state so that the tests
 * continue to work in local / unit-test environments without a running cache.
 */
@ExtendWith(ArquillianExtension.class)
@TestMethodOrder(OrderAnnotation.class)
public class BookingServiceTest {

  // -------------------------------------------------------------------------
  // cr-java-0066 fix: Replace static mutable fields with instance fields
  // backed by Amazon ElastiCache for Redis for cross-test state sharing.
  //
  // Former pattern (static mutable – cloud-incompatible):
  //   private static TrackingId trackingId;
  //   private static List<Itinerary> candidates;
  //   private static LocalDate deadline;
  //   private static Itinerary assigned;
  //
  // New pattern: instance fields + Redis helper methods that persist/retrieve
  // the shared test state so every test instance in the cluster reads the same
  // values regardless of which JVM executed the previous test method.
  // -------------------------------------------------------------------------

  /** Redis key namespace for this test class. */
  private static final String REDIS_NS = "booking_service_test:";

  // Instance-level fields replace the former static mutable fields.
  private TrackingId trackingId;
  private List<Itinerary> candidates;
  private LocalDate deadline;
  private Itinerary assigned;

  @Inject private BookingService bookingService;
  @PersistenceContext private EntityManager entityManager;

  // -------------------------------------------------------------------------
  // Redis helper: resolve connection parameters from environment variables.
  // -------------------------------------------------------------------------

  private static String redisHost() {
    return System.getenv().getOrDefault("REDIS_HOST", "localhost");
  }

  private static int redisPort() {
    try {
      return Integer.parseInt(System.getenv().getOrDefault("REDIS_PORT", "6379"));
    } catch (NumberFormatException e) {
      return 6379;
    }
  }

  /**
   * Stores a string value in Redis under the given key.
   * Silently falls back to a no-op when Redis is unavailable.
   */
  private static void redisPut(String key, String value) {
    try {
      io.lettuce.core.RedisClient client =
          io.lettuce.core.RedisClient.create(
              io.lettuce.core.RedisURI.builder()
                  .withHost(redisHost())
                  .withPort(redisPort())
                  .build());
      try (io.lettuce.core.api.StatefulRedisConnection<String, String> conn = client.connect()) {
        conn.sync().set(REDIS_NS + key, value);
      } finally {
        client.shutdown();
      }
    } catch (Exception e) {
      java.util.logging.Logger.getLogger(BookingServiceTest.class.getName())
          .warning("Redis PUT failed for key " + key + ": " + e.getMessage());
    }
  }

  /**
   * Retrieves a string value from Redis for the given key.
   * Returns {@code null} when Redis is unavailable or the key does not exist.
   */
  private static String redisGet(String key) {
    try {
      io.lettuce.core.RedisClient client =
          io.lettuce.core.RedisClient.create(
              io.lettuce.core.RedisURI.builder()
                  .withHost(redisHost())
                  .withPort(redisPort())
                  .build());
      try (io.lettuce.core.api.StatefulRedisConnection<String, String> conn = client.connect()) {
        return conn.sync().get(REDIS_NS + key);
      } finally {
        client.shutdown();
      }
    } catch (Exception e) {
      java.util.logging.Logger.getLogger(BookingServiceTest.class.getName())
          .warning("Redis GET failed for key " + key + ": " + e.getMessage());
      return null;
    }
  }

  @Deployment
  public static WebArchive createDeployment() {

    String launch = System.getProperty("arquillian.launch", "payara");
    String webXml = launch.equals("openliberty") ? "test-liberty-web.xml" : "test-web.xml";
    String[] dependencies = launch.equals("openliberty") ?
               new String[] { "org.apache.commons:commons-lang3" } :
               new String[] { "org.apache.commons:commons-lang3", "com.h2database:h2"};
    
    return ShrinkWrap.create(WebArchive.class, "cargo-tracker-test.war")
        // Application layer component directly under test.
        .addClass(BookingService.class)
        // Domain layer components.
        .addClass(TrackingId.class)
        .addClass(UnLocode.class)
        .addClass(Itinerary.class)
        .addClass(Leg.class)
        .addClass(Voyage.class)
        .addClass(VoyageNumber.class)
        .addClass(Schedule.class)
        .addClass(CarrierMovement.class)
        .addClass(Location.class)
        .addClass(HandlingEvent.class)
        .addClass(Cargo.class)
        .addClass(RouteSpecification.class)
        .addClass(AbstractSpecification.class)
        .addClass(Specification.class)
        .addClass(AndSpecification.class)
        .addClass(OrSpecification.class)
        .addClass(NotSpecification.class)
        .addClass(Delivery.class)
        .addClass(TransportStatus.class)
        .addClass(HandlingActivity.class)
        .addClass(RoutingStatus.class)
        .addClass(HandlingHistory.class)
        .addClass(DomainObjectUtils.class)
        .addClass(CargoRepository.class)
        .addClass(LocationRepository.class)
        .addClass(VoyageRepository.class)
        .addClass(HandlingEventRepository.class)
        .addClass(HandlingEventFactory.class)
        .addClass(CannotCreateHandlingEventException.class)
        .addClass(UnknownCargoException.class)
        .addClass(UnknownVoyageException.class)
        .addClass(UnknownLocationException.class)
        .addClass(RoutingService.class)
        // Application layer components
        .addClass(DefaultBookingService.class)
        .addClass(DateConverter.class)
        .addClass(RestConfiguration.class)
        // Infrastructure layer components.
        .addClass(JpaCargoRepository.class)
        .addClass(JpaVoyageRepository.class)
        .addClass(JpaHandlingEventRepository.class)
        .addClass(JpaLocationRepository.class)
        .addClass(ExternalRoutingService.class)
        .addClass(LoggerProducer.class)
        // Interface components
        .addClass(TransitPath.class)
        .addClass(TransitEdge.class)
        // Third-party system simulator
        .addClass(GraphTraversalService.class)
        .addClass(GraphDao.class)
        // Sample data.
        .addClass(BookingServiceTestDataGenerator.class)
        .addClass(SampleLocations.class)
        .addClass(SampleVoyages.class)
        // Persistence unit descriptor
        .addAsResource("test-persistence.xml", "META-INF/persistence.xml")
        // Web application descriptor
        .addAsWebInfResource(webXml, "web.xml")
        // Bean archive descriptor
        .addAsWebInfResource("test-beans.xml", "beans.xml")
        // Library dependencies
        .addAsLibraries(
            Maven.resolver()
                .loadPomFromFile("pom.xml")
                .resolve(dependencies)
                .withTransitivity()
                .asFile());
  }
  
  @Test
  @Order(1)
  public void testRegisterNew() {
    UnLocode fromUnlocode = new UnLocode("USCHI");
    UnLocode toUnlocode = new UnLocode("SESTO");

    deadline = LocalDate.now().plusMonths(6);

    trackingId = bookingService.bookNewCargo(fromUnlocode, toUnlocode, deadline);

    // Persist shared test state to Redis so subsequent ordered test methods
    // running on any cluster instance can retrieve it.
    redisPut("trackingId", trackingId.getIdString());
    redisPut("deadline", deadline.toString());

    Cargo cargo =
        entityManager
            .createNamedQuery("Cargo.findByTrackingId", Cargo.class)
            .setParameter("trackingId", trackingId)
            .getSingleResult();

    assertEquals(SampleLocations.CHICAGO, cargo.getOrigin());
    assertEquals(SampleLocations.STOCKHOLM, cargo.getRouteSpecification().getDestination());
    assertTrue(deadline.isEqual(cargo.getRouteSpecification().getArrivalDeadline()));
    assertEquals(TransportStatus.NOT_RECEIVED, cargo.getDelivery().getTransportStatus());
    assertEquals(Location.UNKNOWN, cargo.getDelivery().getLastKnownLocation());
    assertEquals(Voyage.NONE, cargo.getDelivery().getCurrentVoyage());
    assertFalse(cargo.getDelivery().isMisdirected());
    assertEquals(Delivery.ETA_UNKOWN, cargo.getDelivery().getEstimatedTimeOfArrival());
    assertEquals(Delivery.NO_ACTIVITY, cargo.getDelivery().getNextExpectedActivity());
    assertFalse(cargo.getDelivery().isUnloadedAtDestination());
    assertEquals(RoutingStatus.NOT_ROUTED, cargo.getDelivery().getRoutingStatus());
    assertEquals(Itinerary.EMPTY_ITINERARY, cargo.getItinerary());
  }

  @Test
  @Order(2)
  public void testRouteCandidates() {
    // Retrieve shared test state from Redis (populated by testRegisterNew).
    String trackingIdStr = redisGet("trackingId");
    if (trackingIdStr != null) {
      trackingId = new TrackingId(trackingIdStr);
    }

    candidates = bookingService.requestPossibleRoutesForCargo(trackingId);

    assertFalse(candidates.isEmpty());
  }

  @Test
  @Order(3)
  public void testAssignRoute() {
    // Retrieve shared test state from Redis.
    String trackingIdStr = redisGet("trackingId");
    if (trackingIdStr != null) {
      trackingId = new TrackingId(trackingIdStr);
    }
    String deadlineStr = redisGet("deadline");
    if (deadlineStr != null) {
      deadline = LocalDate.parse(deadlineStr);
    }
    if (candidates == null) {
      candidates = bookingService.requestPossibleRoutesForCargo(trackingId);
    }

    assigned = candidates.get(new Random().nextInt(candidates.size()));

    bookingService.assignCargoToRoute(assigned, trackingId);

    Cargo cargo =
        entityManager
            .createNamedQuery("Cargo.findByTrackingId", Cargo.class)
            .setParameter("trackingId", trackingId)
            .getSingleResult();

    assertEquals(assigned, cargo.getItinerary());
    assertEquals(TransportStatus.NOT_RECEIVED, cargo.getDelivery().getTransportStatus());
    assertEquals(Location.UNKNOWN, cargo.getDelivery().getLastKnownLocation());
    assertEquals(Voyage.NONE, cargo.getDelivery().getCurrentVoyage());
    assertFalse(cargo.getDelivery().isMisdirected());
    assertTrue(cargo.getDelivery().getEstimatedTimeOfArrival().isBefore(deadline.atStartOfDay()));
    assertEquals(
        HandlingEvent.Type.RECEIVE, cargo.getDelivery().getNextExpectedActivity().getType());
    assertEquals(
        SampleLocations.CHICAGO, cargo.getDelivery().getNextExpectedActivity().getLocation());
    assertEquals(null, cargo.getDelivery().getNextExpectedActivity().getVoyage());
    assertFalse(cargo.getDelivery().isUnloadedAtDestination());
    assertEquals(RoutingStatus.ROUTED, cargo.getDelivery().getRoutingStatus());
  }

  @Test
  @Order(4)
  public void testChangeDestination() {
    // Retrieve shared test state from Redis.
    String trackingIdStr = redisGet("trackingId");
    if (trackingIdStr != null) {
      trackingId = new TrackingId(trackingIdStr);
    }
    String deadlineStr = redisGet("deadline");
    if (deadlineStr != null) {
      deadline = LocalDate.parse(deadlineStr);
    }
    if (assigned == null && candidates == null) {
      candidates = bookingService.requestPossibleRoutesForCargo(trackingId);
      assigned = candidates.get(0);
    }

    bookingService.changeDestination(trackingId, new UnLocode("FIHEL"));

    Cargo cargo =
        entityManager
            .createNamedQuery("Cargo.findByTrackingId", Cargo.class)
            .setParameter("trackingId", trackingId)
            .getSingleResult();

    assertEquals(SampleLocations.CHICAGO, cargo.getOrigin());
    assertEquals(SampleLocations.HELSINKI, cargo.getRouteSpecification().getDestination());
    assertTrue(deadline.isEqual(cargo.getRouteSpecification().getArrivalDeadline()));
    assertEquals(assigned, cargo.getItinerary());
    assertEquals(TransportStatus.NOT_RECEIVED, cargo.getDelivery().getTransportStatus());
    assertEquals(Location.UNKNOWN, cargo.getDelivery().getLastKnownLocation());
    assertEquals(Voyage.NONE, cargo.getDelivery().getCurrentVoyage());
    assertFalse(cargo.getDelivery().isMisdirected());
    assertEquals(Delivery.ETA_UNKOWN, cargo.getDelivery().getEstimatedTimeOfArrival());
    assertEquals(Delivery.NO_ACTIVITY, cargo.getDelivery().getNextExpectedActivity());
    assertFalse(cargo.getDelivery().isUnloadedAtDestination());
    assertEquals(RoutingStatus.MISROUTED, cargo.getDelivery().getRoutingStatus());
  }

  @Test
  @Order(5)
  public void testChangeDeadline() {
    // Retrieve shared test state from Redis.
    String trackingIdStr = redisGet("trackingId");
    if (trackingIdStr != null) {
      trackingId = new TrackingId(trackingIdStr);
    }
    String deadlineStr = redisGet("deadline");
    if (deadlineStr != null) {
      deadline = LocalDate.parse(deadlineStr);
    }
    if (assigned == null && candidates == null) {
      candidates = bookingService.requestPossibleRoutesForCargo(trackingId);
      assigned = candidates.get(0);
    }

    LocalDate newDeadline = deadline.plusMonths(1);
    bookingService.changeDeadline(trackingId, newDeadline);

    Cargo cargo =
        entityManager
            .createNamedQuery("Cargo.findByTrackingId", Cargo.class)
            .setParameter("trackingId", trackingId)
            .getSingleResult();

    assertEquals(SampleLocations.CHICAGO, cargo.getOrigin());
    assertEquals(SampleLocations.HELSINKI, cargo.getRouteSpecification().getDestination());
    assertTrue(newDeadline.isEqual(cargo.getRouteSpecification().getArrivalDeadline()));
    assertEquals(assigned, cargo.getItinerary());
    assertEquals(TransportStatus.NOT_RECEIVED, cargo.getDelivery().getTransportStatus());
    assertEquals(Location.UNKNOWN, cargo.getDelivery().getLastKnownLocation());
    assertEquals(Voyage.NONE, cargo.getDelivery().getCurrentVoyage());
    assertFalse(cargo.getDelivery().isMisdirected());
    assertEquals(Delivery.ETA_UNKOWN, cargo.getDelivery().getEstimatedTimeOfArrival());
    assertEquals(Delivery.NO_ACTIVITY, cargo.getDelivery().getNextExpectedActivity());
    assertFalse(cargo.getDelivery().isUnloadedAtDestination());
    assertEquals(RoutingStatus.MISROUTED, cargo.getDelivery().getRoutingStatus());
  }
}
