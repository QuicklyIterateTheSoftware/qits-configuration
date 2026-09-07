package eu.wohlben.qits.configuration.control;

import eu.wohlben.qits.configuration.error.BadRequestException;
import java.util.regex.Pattern;

/**
 * What an env name, an application name and an entry key may look like. Untrusted input, checked
 * where it is stored.
 *
 * <p><b>The line this class draws is the whole boundary of this service, FOR ENTRY VALUES.</b> It
 * validates the SHAPE of a key and nothing about the value beside it: a mount specification, a
 * published port, a group id or a network alias is read by qits-platform-deployments' {@code
 * ServiceExtras}, which stays the single parser of those on the platform. Two parsers would be two
 * opinions about what a deployment means, and this one would be the copy that is never exercised by
 * a real deployment.
 *
 * <p><b>THE DOCTRINE AMENDMENT.</b> That line is about the one document class this service used to
 * see — a value somebody stored. There is a SECOND one now: {@code .config/qits/configuration.yml},
 * the declaration an application makes about its own keys, which arrives here because there is
 * nowhere else it could be answered from. This service is the ONE strict parser of THAT document
 * ({@link DeclarationParser}) for the same reason qits-platform-deployments is the one parser of an
 * extras value: whoever serves the typed read has to understand the type. Nothing about the
 * amendment loosens the first line — a declared key's NAME still comes through {@link #requireKey}
 * below, and a stored value is still never read.
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

  /**
   * An environment name, held to the SAME discipline as an application name and by the same pattern
   * — {@code dev}, {@code prod}, {@code review-1234}.
   *
   * <p>One charset rather than a looser one of its own, because an env is now a path segment on this
   * API and a column that leads a unique constraint. Two spellings that differ only in case would be
   * two environments in the store and one environment to a person, which is the kind of ambiguity a
   * configuration service is the worst place to have.
   */
  private static final Pattern ENV = APPLICATION;

  /** {@code env.<VAR>} — an environment variable, in the charset a shell will accept as a name. */
  private static final Pattern ENV_KEY = Pattern.compile("^env\\.[A-Za-z_][A-Za-z0-9_]*$");

  /**
   * {@code mounts[i]}, {@code publishes[i]}, {@code groups[i]}, {@code aliases[i]} — the indexed
   * families. Four digits is a bound rather than a limit anybody will reach; what it refuses is an
   * index that is really a payload.
   */
  private static final Pattern INDEXED_KEY =
      Pattern.compile("^(mounts|publishes|groups|aliases)\\[[0-9]{1,4}]$");

  /**
   * A declaration version — the tag or release version the document was published under.
   *
   * <p><b>Looser than every other name here, and deliberately so.</b> An env and an application are
   * dns labels because they become network aliases; a version becomes nothing but a path segment and
   * a lookup key, and it is not this service's to invent — it is whatever qits-ci released the
   * application as ({@code 2026.907.135446}) or whatever tag the deployer holds. Refusing a spelling
   * the pipeline already published would make this service the reason a real release cannot be
   * declared, which is the one failure a declaration store must not have. What it still refuses is a
   * value that is really a payload: a leading dot or dash, a slash, a space, anything that would let
   * a version escape its path segment.
   */
  private static final Pattern DECLARATION_VERSION = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]*$");

  /** The platform plane: one instance for every environment, reached at a bare wire alias. */
  public static final String TARGET_PLATFORM = "platform";

  /** The environment plane: one instance per tier, reached at {@code <env>-<application>}. */
  public static final String TARGET_ENVIRONMENT = "environment";

  private static final int APPLICATION_MAX = 64;
  private static final int ENV_MAX = 64;
  private static final int KEY_MAX = 256;
  private static final int DECLARATION_VERSION_MAX = 128;

  private ConfigurationKeys() {}

  /** The env name, or a 400 naming the grammar it missed. */
  public static String requireEnv(String env) {
    if (env == null || env.isBlank()) {
      throw new BadRequestException("An environment name is required");
    }
    String trimmed = env.trim();
    if (trimmed.length() > ENV_MAX) {
      throw new BadRequestException(
          "The environment name is longer than " + ENV_MAX + " characters: " + trimmed);
    }
    if (!ENV.matcher(trimmed).matches()) {
      throw new BadRequestException(
          "Not a valid environment name: "
              + trimmed
              + ". It must be lower case, start with a letter, end with a letter or a digit, and"
              + " hold only letters, digits and dashes in between.");
    }
    return trimmed;
  }

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

  /** The declaration version, or a 400 naming the grammar it missed. */
  public static String requireDeclarationVersion(String version) {
    if (version == null || version.isBlank()) {
      throw new BadRequestException("A declaration version is required");
    }
    String trimmed = version.trim();
    if (trimmed.length() > DECLARATION_VERSION_MAX) {
      throw new BadRequestException(
          "The declaration version is longer than "
              + DECLARATION_VERSION_MAX
              + " characters: "
              + trimmed);
    }
    if (!DECLARATION_VERSION.matcher(trimmed).matches()) {
      throw new BadRequestException(
          "Not a valid declaration version: "
              + trimmed
              + ". It must start with a letter or a digit and hold only letters, digits, dots,"
              + " dashes and underscores.");
    }
    return trimmed;
  }

  /**
   * The deployment target a declaration was seeded with, or a 400 naming the two words it may be.
   *
   * <p><b>It comes with the SEED and not out of the document</b>, which is the one thing worth
   * saying twice. Which plane an application deploys onto is the deployer's fact, decided by {@code
   * deployment_target} in its {@code .config/qits/deployments.yml} and known to the pipeline that
   * is posting the declaration; an application asserting its own plane in a file this service reads
   * would be a second answer to a question qits-platform-deployments already answers, and the two
   * would diverge on the first plane move. So the intake takes it as a parameter and this service
   * stores what it was told.
   */
  public static String requireDeploymentTarget(String target) {
    if (target == null || target.isBlank()) {
      throw new BadRequestException(
          "A deploymentTarget is required. It is `"
              + TARGET_PLATFORM
              + "` or `"
              + TARGET_ENVIRONMENT
              + "`, and it is the deployer's fact rather than the document's.");
    }
    String trimmed = target.trim();
    if (!TARGET_PLATFORM.equals(trimmed) && !TARGET_ENVIRONMENT.equals(trimmed)) {
      throw new BadRequestException(
          "Not a valid deploymentTarget: "
              + trimmed
              + ". It is `"
              + TARGET_PLATFORM
              + "` or `"
              + TARGET_ENVIRONMENT
              + "`.");
    }
    return trimmed;
  }

  /** The value, or a 400. Null is refused; the empty string is a value and is kept. */
  public static String requireValue(String value) {
    if (value == null) {
      throw new BadRequestException("A value is required. Removing an entry is a DELETE.");
    }
    return value;
  }
}
