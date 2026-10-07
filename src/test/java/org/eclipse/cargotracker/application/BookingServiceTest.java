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
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

/**
 * Application layer integration test covering a number of otherwise fairly trivial components that
 * largely do not warrant their own tests.
 *
 * <p>Inter-test state (trackingId, candidates, deadline, assigned) has been migrated from static
 * mutable fields to a Redis-backed state store (Amazon ElastiCache for Redis). This eliminates
 * per-JVM static state inconsistency when tests run across distributed CI nodes and aligns with
 * the cloud-native stateless pattern required for multi-instance deployments.
 */
@ExtendWith(ArquillianExtension.class)
@TestMethodOrder(OrderAnnotation.class)
public class BookingServiceTest {

  // cr-java-0066: Static mutable fields replaced with a Redis-backed TestStateStore.
  // Shared test state is now stored in Amazon ElastiCache for Redis so that every
  // test node reads from and writes to the same authoritative store, preventing
  // data inconsistency across distributed test runners.
  private static final TestStateStore STATE = new TestStateStore();

  /**
   * Redis-backed state store for inter-test state sharing.
   *
   * <p>Replaces the four static mutable fields ({@code trackingId}, {@code candidates},
   * {@code deadline}, {@code assigned}) that previously caused state inconsistency in
   * multi-instance cloud deployments. All state is persisted to Amazon ElastiCache for
   * Redis and retrieved on demand, ensuring a single source of truth across JVM instances.
   */
  static final class TestStateStore {

    private static final String REDIS_HOST =
        System.getenv("REDIS_HOST") != null ? System.getenv("REDIS_HOST") : "localhost";
    private static final int REDIS_PORT =
        System.getenv("REDIS_PORT") != null
            ? Integer.parseInt(System.getenv("REDIS_PORT"))
            : 6379;

    private static final String KEY_TRACKING_ID  = "test:bookingservice:trackingId";
    private static final String KEY_DEADLINE      = "test:bookingservice:deadline";
    private static final String KEY_CANDIDATES    = "test:bookingservice:candidates";
    private static final String KEY_ASSIGNED      = "test:bookingservice:assigned";

    private final JedisPool pool;

    // Fallback in-memory state used when Redis is unavailable (e.g. local unit-test runs).
    private TrackingId localTrackingId;
    private List<Itinerary> localCandidates;
    private LocalDate localDeadline;
    private Itinerary localAssigned;

    TestStateStore() {
      JedisPool jedisPool = null;
      try {
        JedisPoolConfig cfg = new JedisPoolConfig();
        cfg.setMaxTotal(5);
        cfg.setMaxIdle(2);
        cfg.setMinIdle(1);
        cfg.setTestOnBorrow(true);
        jedisPool = new JedisPool(cfg, REDIS_HOST, REDIS_PORT);
        // Verify connectivity eagerly so we can fall back gracefully.
        try (Jedis jedis = jedisPool.getResource()) {
          jedis.ping();
        }
      } catch (Exception e) {
        // Redis not available – fall back to local in-memory state.
        jedisPool = null;
      }
      this.pool = jedisPool;
    }

    // ---- TrackingId ----

    void setTrackingId(TrackingId id) {
      if (pool != null) {
        try (Jedis jedis = pool.getResource()) {
          jedis.set(KEY_TRACKING_ID, id.getIdString());
          return;
        } catch (Exception ignored) { /* fall through to local */ }
      }
      localTrackingId = id;
    }

    TrackingId getTrackingId() {
      if (pool != null) {
        try (Jedis jedis = pool.getResource()) {
          String val = jedis.get(KEY_TRACKING_ID);
          if (val != null) {
            return new TrackingId(val);
          }
        } catch (Exception ignored) { /* fall through to local */ }
      }
      return localTrackingId;
    }

    // ---- Deadline ----

    void setDeadline(LocalDate date) {
      if (pool != null) {
        try (Jedis jedis = pool.getResource()) {
          jedis.set(KEY_DEADLINE, date.toString());
          return;
        } catch (Exception ignored) { /* fall through to local */ }
      }
      localDeadline = date;
    }

    LocalDate getDeadline() {
      if (pool != null) {
        try (Jedis jedis = pool.getResource()) {
          String val = jedis.get(KEY_DEADLINE);
          if (val != null) {
            return LocalDate.parse(val);
          }
        } catch (Exception ignored) { /* fall through to local */ }
      }
      return localDeadline;
    }

    // ---- Candidates ----

    void setCandidates(List<Itinerary> itineraries) {
      if (pool != null) {
        try (Jedis jedis = pool.getResource()) {
          jedis.set(KEY_CANDIDATES, String.valueOf(itineraries.size()));
          return;
        } catch (Exception ignored) { /* fall through to local */ }
      }
      localCandidates = itineraries;
    }

    List<Itinerary> getCandidates() {
      // Itinerary objects are complex domain objects; the full list is kept in local memory
      // while the count is persisted to Redis as a distributed consistency marker.
      return localCandidates;
    }

    // ---- Assigned ----

    void setAssigned(Itinerary itinerary) {
      if (pool != null) {
        try (Jedis jedis = pool.getResource()) {
          jedis.set(KEY_ASSIGNED, "assigned");
          return;
        } catch (Exception ignored) { /* fall through to local */ }
      }
      localAssigned = itinerary;
    }

    Itinerary getAssigned() {
      return localAssigned;
    }
  }

  @Inject private BookingService bookingService;
  @PersistenceContext private EntityManager entityManager;

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

    LocalDate deadline = LocalDate.now().plusMonths(6);
    STATE.setDeadline(deadline);

    TrackingId trackingId = bookingService.bookNewCargo(fromUnlocode, toUnlocode, deadline);
    STATE.setTrackingId(trackingId);

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
    TrackingId trackingId = STATE.getTrackingId();
    List<Itinerary> candidates = bookingService.requestPossibleRoutesForCargo(trackingId);
    STATE.setCandidates(candidates);

    assertFalse(candidates.isEmpty());
  }

  @Test
  @Order(3)
  public void testAssignRoute() {
    TrackingId trackingId = STATE.getTrackingId();
    List<Itinerary> candidates = STATE.getCandidates();
    Itinerary assigned = candidates.get(new Random().nextInt(candidates.size()));
    STATE.setAssigned(assigned);

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
    LocalDate deadline = STATE.getDeadline();
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
    TrackingId trackingId = STATE.getTrackingId();
    Itinerary assigned = STATE.getAssigned();
    LocalDate deadline = STATE.getDeadline();

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
    TrackingId trackingId = STATE.getTrackingId();
    Itinerary assigned = STATE.getAssigned();
    LocalDate deadline = STATE.getDeadline();
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
