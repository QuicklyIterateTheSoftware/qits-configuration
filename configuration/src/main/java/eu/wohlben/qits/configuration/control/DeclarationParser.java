package eu.wohlben.qits.configuration.control;

import eu.wohlben.qits.configuration.error.BadRequestException;
import eu.wohlben.qits.configuration.error.DeclarationParseException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

/**
 * THE ONE STRICT PARSER of {@code .config/qits/configuration.yml} — the document in which an
 * application declares which configuration keys it has, what type each one is, and what it falls
 * back to when nobody has set it.
 *
 * <p><b>Why a parser exists here at all, in the service whose first rule is that it parses
 * nothing.</b> That rule is about entry VALUES and it is untouched: what a mount specification or a
 * published port MEANS is still qits-platform-deployments' {@code ServiceExtras} and nothing here
 * reads a stored value. A declaration is a different document class with a different owner. It says
 * {@code env.QITS_EVENTS_URL is a serviceAddress of qits-events on 8080}, and the only place that
 * sentence can be turned into a value is the place that serves the resolved read — because the
 * answer depends on which environment is asking and on which plane the named service deploys onto,
 * two facts no application knows about itself. A second parser of this document elsewhere would be a
 * second opinion about what an application declared, and it would be the copy no deployment
 * exercises. So: one document class, one parser, this one.
 *
 * <p><b>Strict in both directions, and closed at every level.</b> The top level takes {@code keys}
 * and nothing else. A key takes the attributes of its own type and nothing else. A type is one of
 * five words. Every one of those closures is a refusal a person reads in a build log rather than a
 * silently dropped line — the same argument {@link ConfigurationKeys} makes about a key the deployer
 * would not recognise, one document up: a declaration that quietly ignored {@code path:} would
 * produce an address nobody meant, and the container would boot, pass its gate and dial the wrong
 * port. Duplicate keys are refused by the loader for the same reason; "the last one wins" is not an
 * answer a store of record may give.
 *
 * <p><b>Read with {@code SafeConstructor}</b>, which yields plain maps, lists and scalars. No
 * document can name a class into existence, so nothing here needs native-image reflection
 * registration and nothing gets constructed by a file somebody committed. qits-projects'
 * {@code QitsConfigParser} is the precedent and the same three lines.
 *
 * <p><b>What {@code service:} is, and what it becomes.</b> It is the deployer's own APPLICATION
 * NAME, carrying the {@code qits-} prefix exactly as the target's {@code .config/qits/deployments.yml}
 * spells it — {@code qits-events}, {@code qits-observability}, {@code qits-platform-idp} — which is
 * why it is validated against the application grammar and not a looser one. The HOSTNAME it renders
 * to is the deployer's wire alias for that application, and the alias depends on the plane the
 * target deploys onto: {@code <application>} bare on the PLATFORM plane, {@code <env>-<application>}
 * on the ENVIRONMENT plane. That is {@code PdNetworks.alias} restated, and it is restated rather
 * than guessed because it is the difference between {@code http://qits-events:8080} and
 * {@code http://dev-qits-ci:8080}. The plane is not in this document: it is the deployment_target
 * the target's own declaration was seeded with, which is why rendering happens at the resolved read
 * and not here.
 *
 * <p><b>There is no {@code path} and no {@code default} on a serviceAddress</b>, deliberately. A
 * path would make the same key mean an origin to one consumer and a URL to another, and a default
 * would be an address the platform did not render — the one thing a rendered key exists to prevent.
 * A packageVersion carries no default either: the version an application runs is what a release put
 * there, and a hard-coded fallback is how a container starts on a tag nobody shipped.
 */
public final class DeclarationParser {

  /** A free-form string. The ordinary case, and the only type whose default needs no shape. */
  public static final String TYPE_STRING = "string";

  /** {@code true} or {@code false}, stored and served as those two words. */
  public static final String TYPE_BOOLEAN = "boolean";

  /** A decimal number, stored and served as the text it was written as. */
  public static final String TYPE_NUMBER = "number";

  /** An address the PLATFORM renders — see the class javadoc. Never set by hand. */
  public static final String TYPE_SERVICE_ADDRESS = "serviceAddress";

  /** The version of a named package, supplied by whatever released it. Omitted when unset. */
  public static final String TYPE_PACKAGE_VERSION = "packageVersion";

  /** The one top-level key, and the whole top level. */
  private static final String KEYS = "keys";

  /** The vocabulary, in the order every refusal lists it. */
  private static final Set<String> TYPES =
      new LinkedHashSet<>(
          List.of(
              TYPE_STRING, TYPE_BOOLEAN, TYPE_NUMBER, TYPE_SERVICE_ADDRESS, TYPE_PACKAGE_VERSION));

  private static final String ATTRIBUTE_TYPE = "type";
  private static final String ATTRIBUTE_DEFAULT = "default";
  private static final String ATTRIBUTE_DESCRIPTION = "description";
  private static final String ATTRIBUTE_SERVICE = "service";
  private static final String ATTRIBUTE_PORT = "port";
  private static final String ATTRIBUTE_PACKAGE = "package";
  private static final String ATTRIBUTE_NAME = "name";

  private static final Set<String> VALUED_ATTRIBUTES =
      Set.of(ATTRIBUTE_TYPE, ATTRIBUTE_DEFAULT, ATTRIBUTE_DESCRIPTION);
  private static final Set<String> SERVICE_ADDRESS_ATTRIBUTES =
      Set.of(ATTRIBUTE_TYPE, ATTRIBUTE_SERVICE, ATTRIBUTE_PORT, ATTRIBUTE_DESCRIPTION);
  private static final Set<String> PACKAGE_VERSION_ATTRIBUTES =
      Set.of(ATTRIBUTE_TYPE, ATTRIBUTE_PACKAGE, ATTRIBUTE_DESCRIPTION);
  private static final Set<String> PACKAGE_ATTRIBUTES = Set.of(ATTRIBUTE_TYPE, ATTRIBUTE_NAME);

  private static final int PORT_MIN = 1;
  private static final int PORT_MAX = 65535;

  private DeclarationParser() {}

  /**
   * One parsed declaration document.
   *
   * <p>{@code raw} is kept verbatim beside the parsed form, and that is not redundancy. The parsed
   * keys are what this service answers with; the raw text is what the application actually committed
   * — comments, {@code description:} lines and all — and it is the only thing that can settle an
   * argument about whether a version declared what somebody remembers it declaring. {@code
   * contentHash} is over exactly those bytes, so "the same document" is a decision about the file
   * and not about this parser's opinion of it.
   */
  public record Declaration(
      String application, String version, String contentHash, String raw, List<DeclaredKey> keys) {}

  /**
   * One declared key, flattened: the union of what every type carries, with the fields its own type
   * does not use left null.
   *
   * <p>A record per type would be tidier in Java and worse everywhere else — it is one table, one
   * DTO and one wire shape either way, and a sealed hierarchy would buy an exhaustive switch at the
   * price of five of everything.
   *
   * <p>{@code description} is deliberately NOT here. It is for the person reading the yml, it is
   * carried verbatim in {@link Declaration#raw}, and giving it a column would make it look like
   * something a consumer is meant to display.
   */
  public record DeclaredKey(
      String key,
      String type,
      String defaultValue,
      String serviceRef,
      Integer servicePort,
      String packageType,
      String packageName) {}

  /**
   * Parse one declaration document, or refuse it naming the document and the key.
   *
   * @param application the application the document belongs to, dns-label-shaped
   * @param version the version it is published under
   * @param raw the document, exactly as it was posted
   */
  public static Declaration parse(String application, String version, String raw) {
    String app = ConfigurationKeys.requireApplication(application);
    String tag = ConfigurationKeys.requireDeclarationVersion(version);

    if (raw == null || raw.isBlank()) {
      throw refusal(
          app, tag, null, "the document is empty. It must carry a `keys` mapping, even an empty one.");
    }

    Object root;
    try {
      LoaderOptions options = new LoaderOptions();
      // A key declared twice is an ambiguity, not a correction. SnakeYAML's default is last-wins,
      // which in a store of record is the worst of the three possible answers.
      options.setAllowDuplicateKeys(false);
      root = new Yaml(new SafeConstructor(options)).load(raw);
    } catch (YAMLException notYaml) {
      throw refusal(app, tag, null, "not readable as YAML: " + oneLine(notYaml.getMessage()));
    }

    Map<String, Object> document = mapping(app, tag, null, "the document", root);
    for (String top : document.keySet()) {
      if (!KEYS.equals(top)) {
        throw refusal(
            app,
            tag,
            null,
            "unknown top-level key `"
                + top
                + "`. The document carries exactly one, `keys`; the deployment target comes from the"
                + " deployer with the seed, not from this file.");
      }
    }
    if (!document.containsKey(KEYS)) {
      throw refusal(
          app, tag, null, "no `keys` mapping. A declaration that declares nothing writes `keys: {}`.");
    }

    Object keysNode = document.get(KEYS);
    // `keys:` with nothing under it parses as null and means an application that declares no keys —
    // a complete statement, and one a service that has none must be able to make.
    Map<String, Object> declared =
        keysNode == null ? Map.of() : mapping(app, tag, null, "`keys`", keysNode);

    List<DeclaredKey> parsed = new ArrayList<>(declared.size());
    for (Map.Entry<String, Object> each : declared.entrySet()) {
      parsed.add(parseKey(app, tag, each.getKey(), each.getValue()));
    }
    return new Declaration(app, tag, sha256(raw), raw, List.copyOf(parsed));
  }

  // ---------------------------------------------------------------- one key

  private static DeclaredKey parseKey(String app, String tag, String name, Object node) {
    // The NAME goes through the same grammar a stored key does. One grammar, checked in one place:
    // a key this service would refuse at a PUT must not be declarable either, or a declaration could
    // promise a default for a key nobody can ever set.
    String key;
    try {
      key = ConfigurationKeys.requireKey(name);
    } catch (BadRequestException notAKey) {
      throw refusal(app, tag, name, oneLine(notAKey.getMessage()));
    }

    Map<String, Object> attributes = mapping(app, tag, key, "the key", node);
    String type = text(attributes.get(ATTRIBUTE_TYPE));
    if (type == null || type.isBlank()) {
      throw refusal(app, tag, key, "no type. " + typeVocabulary());
    }
    if (!TYPES.contains(type)) {
      throw refusal(app, tag, key, "unknown type '" + type + "'. " + typeVocabulary());
    }

    return switch (type) {
      case TYPE_SERVICE_ADDRESS -> serviceAddress(app, tag, key, attributes);
      case TYPE_PACKAGE_VERSION -> packageVersion(app, tag, key, attributes);
      default -> valued(app, tag, key, type, attributes);
    };
  }

  /** string, boolean and number: the three types whose value is simply stored. */
  private static DeclaredKey valued(
      String app, String tag, String key, String type, Map<String, Object> attributes) {
    closed(app, tag, key, attributes, VALUED_ATTRIBUTES, "a " + type + " key", "type, default and description");

    if (!attributes.containsKey(ATTRIBUTE_DEFAULT)) {
      // NO DEFAULT IS A DECLARATION TOO: the key exists, its type is known, and until somebody sets
      // it the resolved read simply does not carry it. That is different from a default of "".
      return new DeclaredKey(key, type, null, null, null, null, null);
    }
    Object node = attributes.get(ATTRIBUTE_DEFAULT);
    if (node instanceof Map || node instanceof List) {
      throw refusal(app, tag, key, "default must be a scalar, not " + shapeOf(node) + ".");
    }
    if (node == null) {
      throw refusal(
          app,
          tag,
          key,
          "default is empty. Leave the attribute off to declare a key with no default, or write `\"\"`"
              + " to default it to the empty string.");
    }
    // NOT trimmed, and not routed through text(): `default: \"\"` is a value — a key whose default is
    // the empty string is exactly the case the entry table already goes out of its way to keep
    // distinguishable from an absent one, and trimming would collapse `\" \"` into it as well.
    String value = node instanceof String written ? written : String.valueOf(node);

    return switch (type) {
      case TYPE_BOOLEAN -> {
        if (!"true".equals(value) && !"false".equals(value)) {
          throw refusal(app, tag, key, "default must be true or false, not '" + value + "'.");
        }
        yield new DeclaredKey(key, type, value, null, null, null, null);
      }
      case TYPE_NUMBER -> {
        try {
          new BigDecimal(value);
        } catch (NumberFormatException notANumber) {
          throw refusal(app, tag, key, "default must be a decimal number, not '" + value + "'.");
        }
        yield new DeclaredKey(key, type, value, null, null, null, null);
      }
      default -> new DeclaredKey(key, type, value, null, null, null, null);
    };
  }

  private static DeclaredKey serviceAddress(
      String app, String tag, String key, Map<String, Object> attributes) {
    closed(
        app,
        tag,
        key,
        attributes,
        SERVICE_ADDRESS_ATTRIBUTES,
        "a serviceAddress key",
        "type, service, port and description");

    String service = text(attributes.get(ATTRIBUTE_SERVICE));
    if (service == null) {
      throw refusal(
          app,
          tag,
          key,
          "no service. A serviceAddress names the application it addresses, by the application name"
              + " that application deploys under (`qits-events`, not `events`).");
    }
    try {
      service = ConfigurationKeys.requireApplication(service);
    } catch (BadRequestException notAnApplication) {
      throw refusal(app, tag, key, "service " + oneLine(notAnApplication.getMessage()));
    }

    if (!attributes.containsKey(ATTRIBUTE_PORT)) {
      throw refusal(
          app,
          tag,
          key,
          "no port. A serviceAddress has no default port: the one every qits service listens on is a"
              + " convention, and a convention rendered into somebody's environment is the address"
              + " that is wrong the first time it changes.");
    }
    Object portNode = attributes.get(ATTRIBUTE_PORT);
    String portText = portNode instanceof Map || portNode instanceof List ? null : text(portNode);
    int port;
    try {
      port = Integer.parseInt(portText == null ? "" : portText);
    } catch (NumberFormatException notAPort) {
      throw refusal(
          app,
          tag,
          key,
          "port must be a whole number between "
              + PORT_MIN
              + " and "
              + PORT_MAX
              + ", not "
              + quoted(portNode)
              + ".");
    }
    if (port < PORT_MIN || port > PORT_MAX) {
      throw refusal(
          app,
          tag,
          key,
          "port must be a whole number between " + PORT_MIN + " and " + PORT_MAX + ", not " + port + ".");
    }
    return new DeclaredKey(key, TYPE_SERVICE_ADDRESS, null, service, port, null, null);
  }

  private static DeclaredKey packageVersion(
      String app, String tag, String key, Map<String, Object> attributes) {
    closed(
        app,
        tag,
        key,
        attributes,
        PACKAGE_VERSION_ATTRIBUTES,
        "a packageVersion key",
        "type, package and description");

    if (!attributes.containsKey(ATTRIBUTE_PACKAGE)) {
      throw refusal(
          app,
          tag,
          key,
          "no package. A packageVersion names the package whose version it carries, as package.type"
              + " and package.name.");
    }
    Map<String, Object> pkg =
        mapping(app, tag, key, "`package`", attributes.get(ATTRIBUTE_PACKAGE));
    closed(app, tag, key, pkg, PACKAGE_ATTRIBUTES, "a package", "type and name");

    String packageType = text(pkg.get(ATTRIBUTE_TYPE));
    if (packageType == null) {
      throw refusal(
          app,
          tag,
          key,
          "package.type is required and must not be blank. It says WHERE the version is a version of"
              + " — `docker`, `binary` — and it is deliberately open: a vocabulary closed here would"
              + " have to be reopened by a migration the first time a new kind of artifact is"
              + " released.");
    }
    String packageName = text(pkg.get(ATTRIBUTE_NAME));
    if (packageName == null) {
      throw refusal(app, tag, key, "package.name is required and must not be blank.");
    }
    return new DeclaredKey(
        key, TYPE_PACKAGE_VERSION, null, null, null, packageType, packageName);
  }

  // ---------------------------------------------------------------- shapes and refusals

  /**
   * The closure check every level runs: no attribute this type does not know about.
   *
   * <p>It refuses rather than ignores, and the whole design of this parser is in that choice. An
   * ignored attribute is a line somebody wrote, committed and believes is in effect.
   */
  private static void closed(
      String app,
      String tag,
      String key,
      Map<String, Object> attributes,
      Set<String> allowed,
      String what,
      String list) {
    for (String attribute : attributes.keySet()) {
      if (!allowed.contains(attribute)) {
        throw refusal(
            app,
            tag,
            key,
            "unknown attribute '" + attribute + "' on " + what + ". It takes " + list + ".");
      }
    }
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> mapping(
      String app, String tag, String key, String what, Object node) {
    if (!(node instanceof Map<?, ?> map)) {
      throw refusal(app, tag, key, what + " must be a mapping, not " + shapeOf(node) + ".");
    }
    for (Object name : map.keySet()) {
      if (!(name instanceof String)) {
        throw refusal(app, tag, key, what + " has a non-text key: " + name + ".");
      }
    }
    return (Map<String, Object>) map;
  }

  /** A scalar as text, trimmed; null when it is absent, null or blank. */
  private static String text(Object node) {
    if (node == null) {
      return null;
    }
    String value = String.valueOf(node).trim();
    return value.isEmpty() ? null : value;
  }

  private static String shapeOf(Object node) {
    if (node == null) {
      return "nothing";
    }
    if (node instanceof Map) {
      return "a mapping";
    }
    if (node instanceof List) {
      return "a list";
    }
    return "the scalar '" + node + "'";
  }

  private static String quoted(Object node) {
    return node == null ? "nothing" : "'" + node + "'";
  }

  private static String typeVocabulary() {
    return "A type is one of " + String.join(", ", TYPES) + ".";
  }

  private static String oneLine(String message) {
    return message == null ? "" : message.replace('\n', ' ').replace('\r', ' ').trim();
  }

  private static DeclarationParseException refusal(
      String application, String version, String key, String what) {
    return new DeclarationParseException(
        "declaration "
            + application
            + "@"
            + version
            + (key == null ? "" : ": key " + key)
            + ": "
            + what);
  }

  /**
   * SHA-256 of the raw bytes, hex.
   *
   * <p>Over the DOCUMENT and not over the parsed keys, so that re-posting a file whose only change
   * is a comment is a conflict rather than a no-op. That sounds strict and is the point: two builds
   * publishing the same version from different sources is exactly what the conflict exists to
   * surface, and "the parts I chose to read are the same" would hide it.
   */
  private static String sha256(String raw) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException impossible) {
      // SHA-256 is required of every JRE. If it is gone, nothing else here is trustworthy either.
      throw new IllegalStateException("SHA-256 is not available", impossible);
    }
  }
}
