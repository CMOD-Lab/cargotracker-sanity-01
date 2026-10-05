package org.eclipse.cargotracker.domain.model.voyage;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.eclipse.cargotracker.domain.model.location.SampleLocations;
import org.eclipse.cargotracker.domain.model.location.UnLocode;

/**
 * Lightweight JSON serialiser / deserialiser for {@link Voyage} objects.
 *
 * <p>Used by {@link VoyageRedisRepository} to persist voyage state in Amazon ElastiCache for
 * Redis. The implementation is intentionally self-contained (no external JSON library required)
 * so that it works with the existing Jakarta EE dependency set.
 *
 * <p>The serialised format is a simple JSON object:
 * <pre>
 * {
 *   "voyageNumber": "V100",
 *   "movements": [
 *     {
 *       "departureLocation": "CNHKG",
 *       "arrivalLocation":   "JPTYO",
 *       "departureTime":     "2023-01-01T06:00:00",
 *       "arrivalTime":       "2023-01-03T18:00:00"
 *     },
 *     ...
 *   ]
 * }
 * </pre>
 */
public final class VoyageSerializer {

  private static final Logger LOGGER = Logger.getLogger(VoyageSerializer.class.getName());

  private VoyageSerializer() {
    // utility class – no instances
  }

  // -------------------------------------------------------------------------
  // Serialisation
  // -------------------------------------------------------------------------

  /**
   * Converts a {@link Voyage} to its JSON representation.
   *
   * @param voyage the voyage to serialise (must not be {@code null})
   * @return a JSON string
   */
  public static String toJson(Voyage voyage) {
    StringBuilder sb = new StringBuilder();
    sb.append("{");
    sb.append("\"voyageNumber\":\"").append(escape(voyage.getVoyageNumber().getIdString())).append("\"");
    sb.append(",\"movements\":[");

    List<CarrierMovement> movements = voyage.getSchedule().getCarrierMovements();
    for (int i = 0; i < movements.size(); i++) {
      CarrierMovement m = movements.get(i);
      if (i > 0) {
        sb.append(",");
      }
      sb.append("{");
      sb.append("\"departureLocation\":\"")
          .append(escape(m.getDepartureLocation().getUnLocode().getIdString()))
          .append("\"");
      sb.append(",\"arrivalLocation\":\"")
          .append(escape(m.getArrivalLocation().getUnLocode().getIdString()))
          .append("\"");
      sb.append(",\"departureTime\":\"").append(m.getDepartureTime().toString()).append("\"");
      sb.append(",\"arrivalTime\":\"").append(m.getArrivalTime().toString()).append("\"");
      sb.append("}");
    }

    sb.append("]}");
    return sb.toString();
  }

  // -------------------------------------------------------------------------
  // Deserialisation
  // -------------------------------------------------------------------------

  /**
   * Reconstructs a {@link Voyage} from its JSON representation.
   *
   * @param json the JSON string produced by {@link #toJson(Voyage)}
   * @return the reconstructed {@link Voyage}, or {@code null} if parsing fails
   */
  public static Voyage fromJson(String json) {
    try {
      String voyageNumberStr = extractStringField(json, "voyageNumber");
      if (voyageNumberStr == null) {
        return null;
      }
      VoyageNumber voyageNumber = new VoyageNumber(voyageNumberStr);

      // Extract the movements array substring.
      int movementsStart = json.indexOf("\"movements\":[");
      if (movementsStart < 0) {
        return null;
      }
      int arrayStart = json.indexOf('[', movementsStart);
      int arrayEnd = findMatchingBracket(json, arrayStart, '[', ']');
      if (arrayEnd < 0) {
        return null;
      }
      String movementsJson = json.substring(arrayStart + 1, arrayEnd);

      List<CarrierMovement> movements = parseMovements(movementsJson);
      if (movements.isEmpty()) {
        return null;
      }

      // Determine the departure location of the first movement.
      org.eclipse.cargotracker.domain.model.location.Location departureLocation =
          movements.get(0).getDepartureLocation();

      Voyage.Builder builder = new Voyage.Builder(voyageNumber, departureLocation);
      for (CarrierMovement m : movements) {
        builder.addMovement(m.getArrivalLocation(), m.getDepartureTime(), m.getArrivalTime());
      }
      return builder.build();

    } catch (Exception e) {
      LOGGER.log(Level.WARNING, "VoyageSerializer: Failed to deserialise voyage from JSON: " + json, e);
      return null;
    }
  }

  // -------------------------------------------------------------------------
  // Private helpers
  // -------------------------------------------------------------------------

  private static List<CarrierMovement> parseMovements(String movementsJson) {
    List<CarrierMovement> result = new ArrayList<>();
    int pos = 0;
    while (pos < movementsJson.length()) {
      int objStart = movementsJson.indexOf('{', pos);
      if (objStart < 0) {
        break;
      }
      int objEnd = findMatchingBracket(movementsJson, objStart, '{', '}');
      if (objEnd < 0) {
        break;
      }
      String movementJson = movementsJson.substring(objStart, objEnd + 1);
      CarrierMovement movement = parseMovement(movementJson);
      if (movement != null) {
        result.add(movement);
      }
      pos = objEnd + 1;
    }
    return result;
  }

  private static CarrierMovement parseMovement(String json) {
    try {
      String depLocCode = extractStringField(json, "departureLocation");
      String arrLocCode = extractStringField(json, "arrivalLocation");
      String depTimeStr = extractStringField(json, "departureTime");
      String arrTimeStr = extractStringField(json, "arrivalTime");

      if (depLocCode == null || arrLocCode == null || depTimeStr == null || arrTimeStr == null) {
        return null;
      }

      org.eclipse.cargotracker.domain.model.location.Location depLoc =
          resolveLocation(depLocCode);
      org.eclipse.cargotracker.domain.model.location.Location arrLoc =
          resolveLocation(arrLocCode);

      if (depLoc == null || arrLoc == null) {
        return null;
      }

      LocalDateTime depTime = LocalDateTime.parse(depTimeStr);
      LocalDateTime arrTime = LocalDateTime.parse(arrTimeStr);

      return new CarrierMovement(depLoc, arrLoc, depTime, arrTime);
    } catch (Exception e) {
      LOGGER.log(Level.WARNING, "VoyageSerializer: Failed to parse movement: " + json, e);
      return null;
    }
  }

  /**
   * Resolves a UN/LOCODE string to a {@link org.eclipse.cargotracker.domain.model.location.Location}
   * using the {@link SampleLocations} catalogue.
   */
  private static org.eclipse.cargotracker.domain.model.location.Location resolveLocation(
      String unLocodeStr) {
    UnLocode unLocode = new UnLocode(unLocodeStr);
    return SampleLocations.lookup(unLocode);
  }

  /**
   * Extracts the string value of a named JSON field from a flat JSON object string.
   * Only handles simple string fields (not nested objects or arrays).
   */
  private static String extractStringField(String json, String fieldName) {
    String search = "\"" + fieldName + "\":\"";
    int start = json.indexOf(search);
    if (start < 0) {
      return null;
    }
    start += search.length();
    int end = json.indexOf('"', start);
    if (end < 0) {
      return null;
    }
    return json.substring(start, end);
  }

  /**
   * Finds the index of the closing bracket that matches the opening bracket at {@code openPos}.
   *
   * @param s       the string to search
   * @param openPos the index of the opening bracket
   * @param open    the opening bracket character ('{' or '[')
   * @param close   the closing bracket character ('}' or ']')
   * @return the index of the matching closing bracket, or -1 if not found
   */
  private static int findMatchingBracket(String s, int openPos, char open, char close) {
    int depth = 0;
    for (int i = openPos; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c == open) {
        depth++;
      } else if (c == close) {
        depth--;
        if (depth == 0) {
          return i;
        }
      }
    }
    return -1;
  }

  /** Escapes special characters in a JSON string value. */
  private static String escape(String value) {
    if (value == null) {
      return "";
    }
    return value.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}
