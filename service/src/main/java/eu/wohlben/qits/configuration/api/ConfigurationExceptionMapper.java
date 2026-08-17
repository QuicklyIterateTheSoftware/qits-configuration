package eu.wohlben.qits.configuration.api;

import eu.wohlben.qits.configuration.error.ConfigurationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.Map;

/**
 * Maps the domain's framework-free {@link ConfigurationException}s (each carrying a status code) to
 * HTTP responses — kept here in {@code service} because the domain module carries no JAX-RS.
 *
 * <p>The envelope is the platform's: {@code {"message": "..."}}, one key, the sentence the domain
 * threw. Every refusal names what was wrong with the value it refused, so a caller's log is where
 * the fix is read — which matters more here than in most services, because the caller is usually a
 * person typing a key by hand.
 */
@Provider
public class ConfigurationExceptionMapper implements ExceptionMapper<ConfigurationException> {

  @Override
  public Response toResponse(ConfigurationException exception) {
    int status = exception.statusCode();
    String message = exception.getMessage();
    if (message == null || message.isBlank()) {
      message = Response.Status.fromStatusCode(status).getReasonPhrase();
    }
    return Response.status(status)
        .entity(Map.of("message", message))
        .type(MediaType.APPLICATION_JSON)
        .build();
  }
}
