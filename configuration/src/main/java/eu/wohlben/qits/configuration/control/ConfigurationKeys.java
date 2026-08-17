package eu.wohlben.qits.configuration.control;

import eu.wohlben.qits.configuration.error.BadRequestException;
import java.util.regex.Pattern;

/**
 * What an application name and an entry key may look like. Untrusted input, checked where it is
 * stored.
 *
 * <p><b>The line this class draws is the whole boundary of this service.</b> It validates the SHAPE
 * of a key and nothing about the value beside it: a mount specification, a published port, a group
 * id or a network alias is read by qits-platform-deployments' {@code ServiceExtras}, which stays the
 * single parser of those on the platform. Two parsers would be two opinions about what a deployment
 * means, and this one would be the copy that is never exercised by a real deployment.
 *
 * <p><b>Why the key is checked at all, then.</b> A key becomes half of a property name the deployer
 * layers into its own configuration ({@code qits.platform.deployments.extras.<app>.<key>}), and the
 * deployer REFUSES a deployment carrying a key it does not recognise — by design, because a dropped
 * flag is a container that boots, passes its gate and has lost its volume. Refusing the key here,
 * at the write, turns that into a 400 the person who typed it reads, instead of a failed deployment
 * hours later.
 *
 * <p>Every refusal names what is wrong with the value it refused.
 */
public final class ConfigurationKeys {

  /**
   * A dns-label-ish application name: lower case, starts with a letter, ends alphanumeric, dashes
   * inside. The same charset the deployer's own identifiers use, because the name reaches network
   * aliases and image path segments over there.
   */
  private static final Pattern APPLICATION = Pattern.compile("^[a-z]([a-z0-9-]{0,62}[a-z0-9])?$");

  /** {@code env.<VAR>} — an environment variable, in the charset a shell will accept as a name. */
  private static final Pattern ENV_KEY = Pattern.compile("^env\\.[A-Za-z_][A-Za-z0-9_]*$");

  /**
   * {@code mounts[i]}, {@code publishes[i]}, {@code groups[i]}, {@code aliases[i]} — the indexed
   * families. Four digits is a bound rather than a limit anybody will reach; what it refuses is an
   * index that is really a payload.
   */
  private static final Pattern INDEXED_KEY =
      Pattern.compile("^(mounts|publishes|groups|aliases)\\[[0-9]{1,4}]$");

  private static final int APPLICATION_MAX = 64;
  private static final int KEY_MAX = 256;

  private ConfigurationKeys() {}

  /** The application name, or a 400 naming what is wrong with it. */
  public static String requireApplication(String application) {
    if (application == null || application.isBlank()) {
      throw new BadRequestException("An application name is required");
    }
    String trimmed = application.trim();
    if (trimmed.length() > APPLICATION_MAX) {
      throw new BadRequestException(
          "The application name is longer than " + APPLICATION_MAX + " characters: " + trimmed);
    }
    if (!APPLICATION.matcher(trimmed).matches()) {
      throw new BadRequestException(
          "Not a valid application name: "
              + trimmed
              + ". It must be lower case, start with a letter, end with a letter or a digit, and"
              + " hold only letters, digits and dashes in between.");
    }
    return trimmed;
  }

  /** The entry key, or a 400 naming which part of the grammar it missed. */
  public static String requireKey(String key) {
    if (key == null || key.isBlank()) {
      throw new BadRequestException("A key is required");
    }
    String trimmed = key.trim();
    if (trimmed.length() > KEY_MAX) {
      throw new BadRequestException(
          "The key is longer than " + KEY_MAX + " characters: " + trimmed);
    }
    if (ENV_KEY.matcher(trimmed).matches() || INDEXED_KEY.matcher(trimmed).matches()) {
      return trimmed;
    }
    if (trimmed.startsWith("env.")) {
      throw new BadRequestException(
          "Not a valid environment variable name in key "
              + trimmed
              + ". After `env.` it must start with a letter or an underscore and hold only letters,"
              + " digits and underscores.");
    }
    int bracket = trimmed.indexOf('[');
    if (bracket > 0) {
      String family = trimmed.substring(0, bracket);
      if (family.equals("mounts")
          || family.equals("publishes")
          || family.equals("groups")
          || family.equals("aliases")) {
        throw new BadRequestException(
            "Not a valid index in key "
                + trimmed
                + ". `"
                + family
                + "` takes one to four digits in square brackets, as in "
                + family
                + "[0].");
      }
    }
    throw new BadRequestException(
        "Not a valid key: "
            + trimmed
            + ". A key is `env.<VAR>` or one of `mounts[i]`, `publishes[i]`, `groups[i]`,"
            + " `aliases[i]`.");
  }

  /** The value, or a 400. Null is refused; the empty string is a value and is kept. */
  public static String requireValue(String value) {
    if (value == null) {
      throw new BadRequestException("A value is required. Removing an entry is a DELETE.");
    }
    return value;
  }
}
