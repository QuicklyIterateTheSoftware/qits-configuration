package eu.wohlben.qits.configuration.error;

/** Configuration error mapped to HTTP 404 by the web layer. */
public class NotFoundException extends ConfigurationException {

  public NotFoundException(String message) {
    super(404, message);
  }
}
