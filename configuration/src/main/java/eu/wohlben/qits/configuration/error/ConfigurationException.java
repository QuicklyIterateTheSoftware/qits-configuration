package eu.wohlben.qits.configuration.error;

/**
 * Base for configuration errors. Carries an HTTP-ish status code so the web layer can map it to a
 * response without this module depending on JAX-RS — the framework-free stance every qits domain jar
 * takes. The {@code service} module maps these via {@code ConfigurationExceptionMapper}.
 */
public class ConfigurationException extends RuntimeException {

  private final int statusCode;

  public ConfigurationException(int statusCode, String message) {
    super(message);
    this.statusCode = statusCode;
  }

  public ConfigurationException(int statusCode, String message, Throwable cause) {
    super(message, cause);
    this.statusCode = statusCode;
  }

  public int statusCode() {
    return statusCode;
  }
}
