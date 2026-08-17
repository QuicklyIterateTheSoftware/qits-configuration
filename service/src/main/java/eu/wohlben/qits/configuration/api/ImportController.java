package eu.wohlben.qits.configuration.api;

import eu.wohlben.qits.configuration.control.ConfigurationService;
import eu.wohlben.qits.configuration.dto.ImportSummaryDto;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;

/**
 * Bulk import of an extras properties file, in the spelling the deployer's own config volume uses:
 *
 * <pre>qits.platform.deployments.extras.&lt;application&gt;.&lt;key&gt;=&lt;value&gt;</pre>
 *
 * <p><b>The body is a real properties file, not a projection of one.</b> It takes {@code
 * text/plain}, comments and blank lines and every unrelated key included, and reports how many lines
 * it ignored. The two callers are the one-time migration off the config volume and the bootstrap's
 * seeding, and both hold a file rather than a list — an endpoint that made them filter it first
 * would put a second parser of the deployer's format in a shell script.
 *
 * <p><b>Idempotent.</b> A line whose value is already stored writes no revision, so re-running an
 * import on every boot is free and the history stays a record of changes rather than of runs.
 *
 * <p><b>One transaction for the whole file.</b> A malformed line late in the file leaves nothing
 * behind — a half-applied import would be worse than a failed one, because the operator would have
 * to work out which half.
 *
 * <p>It sits at {@code /configuration/api/import} rather than under {@code /applications} because it
 * carries its own application segments: the file names them, one per line.
 */
@Path("/import")
@Produces(MediaType.APPLICATION_JSON)
public class ImportController {

  @Inject ConfigurationService configuration;

  @Inject SecurityIdentity identity;

  @POST
  @Consumes(MediaType.TEXT_PLAIN)
  @Operation(summary = "Import an extras properties file, idempotently")
  @APIResponse(responseCode = "200", description = "What the import did")
  @APIResponse(
      responseCode = "400",
      description = "A line carries the extras prefix and a key or application this service refuses")
  @RolesAllowed({"qits:admin", "qits:system"})
  public ImportSummaryDto importProperties(String body) {
    return configuration.importProperties(body, actor());
  }

  /** See the note on {@code ConfigurationController.actor()}: a null is "no name worth recording". */
  private String actor() {
    if (identity == null || identity.isAnonymous() || identity.getPrincipal() == null) {
      return null;
    }
    String name = identity.getPrincipal().getName();
    return name == null || name.isBlank() ? null : name;
  }
}
