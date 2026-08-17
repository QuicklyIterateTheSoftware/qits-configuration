package eu.wohlben.qits.configuration.control;

import java.util.List;

/**
 * The one spelling this service shares with qits-platform-deployments:
 *
 * <pre>qits.platform.deployments.extras.&lt;application&gt;.&lt;key&gt;=&lt;value&gt;</pre>
 *
 * <p><b>Why the full prefix travels on the wire.</b> The resolved read is served as complete
 * property NAMES rather than as bare keys, so the consumer can layer the answer as a configuration
 * source verbatim — no prefix to re-assemble, no second place for the namespace to be spelled, and
 * nothing to get wrong the day the deployer's namespace moves. It moved once already (`qits.cd.*`,
 * then `qits.pd.*`, then today's), and each move was a wrapper edit in every repository that had
 * written the prefix down.
 *
 * <p><b>Splitting is unambiguous because an application name holds no dot.</b> After the prefix, the
 * first dot ends the application segment and everything after it is the key — which is what lets
 * {@code env.QITS_X} and {@code mounts[0]} share one grammar without a second delimiter.
 */
public final class ExtrasProperties {

  /** The deployer's own namespace, with the trailing dot. */
  public static final String PREFIX = "qits.platform.deployments.extras.";

  private ExtrasProperties() {}

  /** The full property name for one entry, as a consumer layers it. */
  public static String propertyName(String application, String key) {
    return PREFIX + application + "." + key;
  }

  /**
   * One line of an extras properties file, split into an application and a key — or empty when the
   * line is not this service's business.
   *
   * <p>Empty means IGNORED and never rejected: an import is handed a real properties file, which
   * carries comments, blank lines and the deployer's other keys, and a bulk import that refused
   * those would be an import nobody could run. What IS rejected is a line that clearly meant to be
   * an entry — it carries the prefix — and is malformed; that goes through {@link
   * ConfigurationKeys} and comes back a 400.
   */
  public static List<Parsed> parse(String text) {
    if (text == null || text.isBlank()) {
      return List.of();
    }
    return text.lines().map(ExtrasProperties::parseLine).filter(Parsed::isPresent).toList();
  }

  private static Parsed parseLine(String rawLine) {
    String line = rawLine.strip();
    if (line.isEmpty() || line.startsWith("#") || line.startsWith("!")) {
      return Parsed.ABSENT;
    }
    int separator = separatorIndex(line);
    if (separator < 0) {
      return Parsed.ABSENT;
    }
    String name = line.substring(0, separator).strip();
    if (!name.startsWith(PREFIX)) {
      return Parsed.ABSENT;
    }
    String value = line.substring(separator + 1).strip();
    String remainder = name.substring(PREFIX.length());
    int dot = remainder.indexOf('.');
    if (dot <= 0 || dot == remainder.length() - 1) {
      // The prefix is there, so the line meant to be an entry — hand it on with an application
      // segment that ConfigurationKeys will refuse by name.
      return new Parsed(remainder, "", value);
    }
    return new Parsed(remainder.substring(0, dot), remainder.substring(dot + 1), value);
  }

  /** {@code =} or {@code :}, whichever comes first — both are properties separators. */
  private static int separatorIndex(String line) {
    int equals = line.indexOf('=');
    int colon = line.indexOf(':');
    if (equals < 0) {
      return colon;
    }
    if (colon < 0) {
      return equals;
    }
    return Math.min(equals, colon);
  }

  /** One recognised line: which application, which key, what value. */
  public record Parsed(String application, String key, String value) {

    static final Parsed ABSENT = new Parsed(null, null, null);

    boolean isPresent() {
      return application != null;
    }
  }
}
