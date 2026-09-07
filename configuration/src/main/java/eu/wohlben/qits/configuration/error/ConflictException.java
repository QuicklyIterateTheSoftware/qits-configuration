package eu.wohlben.qits.configuration.error;

/**
 * Configuration error mapped to HTTP 409 by the web layer: what is stored under this address is not
 * what the caller is asserting, and neither one is wrong enough to overwrite the other.
 *
 * <p>The one caller today is the declaration intake. A version is a fixed point — {@code
 * qits-ci@2026.907.1} names one document forever — so a second, different document under the same
 * version is not an update, it is two builds disagreeing about what that release declared. Taking
 * the newer one would silently rewrite what a deployment already resolved against. Every message
 * here names BOTH sides, because the whole value of the refusal is being able to see which two
 * things collided.
 */
public class ConflictException extends ConfigurationException {

  public ConflictException(String message) {
    super(409, message);
  }
}
