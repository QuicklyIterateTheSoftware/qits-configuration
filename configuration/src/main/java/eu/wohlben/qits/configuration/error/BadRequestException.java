package eu.wohlben.qits.configuration.error;

/**
 * Configuration error mapped to HTTP 400 by the web layer.
 *
 * <p>Every message names what is wrong with the value it refused. A caller writing configuration is
 * usually a person at a terminal or a bootstrap script, and neither reads a stack trace.
 */
public class BadRequestException extends ConfigurationException {

  public BadRequestException(String message) {
    super(400, message);
  }
}
