package eu.wohlben.qits.configuration.api;

import eu.wohlben.qits.configuration.control.ConfigurationService;
import eu.wohlben.qits.configuration.dto.ApplicationSummaryDto;
import eu.wohlben.qits.configuration.dto.ConfigurationEntryDto;
import eu.wohlben.qits.configuration.dto.ConfigurationRevisionDto;
import eu.wohlben.qits.configuration.dto.ResolvedConfigurationDto;
import eu.wohlben.qits.configuration.mapper.ConfigurationMapper;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;

/**
 * Deployment configuration, by application.
 *
 * <p>Served under {@code /configuration/api/applications} — the {@code /configuration/api} prefix is
 * {@code quarkus.rest.path}, not spelled here, so this class carries only its own noun.
 *
 * <p><b>Every route accepts the same pair of roles</b>, {@code qits:admin} (a person, through the
 * gateway's forward-auth headers) and {@code qits:system} (a machine, through a bearer validated
 * against qits-platform-idp). The reads are pulled by the deployer once per deployment and read by
 * an operator in a browser; the writes are made by an operator and by the bootstrap's import. A
 * machine-only guard on either would lock out the other half, which is why none of these calls
 * {@code MachineAuth.require()}. There is no anonymous route here.
 *
 * <p>Request and response shapes are nested records, the platform's controller idiom: the wire
 * contract for one operation lives beside the method that serves it.
 */
@Path("/applications")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ConfigurationController {

  @Inject ConfigurationService configuration;

  @Inject ConfigurationMapper mapper;

  @Inject SecurityIdentity identity;

  public record ListApplicationsResponse(List<ApplicationSummaryDto> applications) {}

  public record ListEntriesResponse(List<ConfigurationEntryDto> entries) {}

  public record ListHistoryResponse(List<ConfigurationRevisionDto> revisions) {}

  public record SetEntryRequest(String value) {

    public record Response(ConfigurationEntryDto entry) {}
  }

  /**
   * Every application this service holds configuration for, with its entry count and how far its
   * history has run.
   *
   * <p>An application whose entries have all been deleted is still listed, at zero entries: "where
   * did my configuration go" is the question this listing most needs to be able to answer.
   */
  @GET
  @Operation(summary = "Every configured application, with entry counts and head revisions")
  @APIResponse(responseCode = "200", description = "The applications")
  @RolesAllowed({"qits:admin", "qits:system"})
  public ListApplicationsResponse applications() {
    return new ListApplicationsResponse(configuration.applications());
  }

  /**
   * THE DEPLOYER'S READ: one application's configuration as a flat property map, at the full
   * prefixed spelling {@code qits.platform.deployments.extras.<app>.<key>}.
   *
   * <p>The names are complete on purpose — a consumer layers this map as a configuration source
   * verbatim, with no prefix to re-assemble and no second place for the deployer's namespace to be
   * written down.
   *
   * <p>{@code headRevision} is what the caller records to say which configuration it deployed with.
   * It comes from the append-only log, so it moves forward on a delete as well as on a write.
   *
   * <p>An application with nothing stored is an empty map at revision 0, never a 404: a deployer
   * that read a 404 as an error would refuse every deployment of an application nobody has
   * configured.
   */
  @GET
  @Path("/{application}/resolved")
  @Operation(summary = "One application's configuration as a flat, fully prefixed property map")
  @APIResponse(responseCode = "200", description = "The resolved properties and the head revision")
  @APIResponse(responseCode = "400", description = "The application name is not valid")
  @RolesAllowed({"qits:admin", "qits:system"})
  public ResolvedConfigurationDto resolved(@PathParam("application") String application) {
    return configuration.resolve(application);
  }

  /** One application's current entries, by key. */
  @GET
  @Path("/{application}/entries")
  @Operation(summary = "One application's current entries")
  @APIResponse(responseCode = "200", description = "The entries")
  @APIResponse(responseCode = "400", description = "The application name is not valid")
  @RolesAllowed({"qits:admin", "qits:system"})
  public ListEntriesResponse entries(@PathParam("application") String application) {
    return new ListEntriesResponse(
        configuration.entriesOf(application).stream().map(mapper::toDto).toList());
  }

  /**
   * Set one entry's value.
   *
   * <p>201 the first time a key is seen, 200 afterwards. <b>An identical value writes no
   * revision</b> and answers 200 with the entry unchanged — which is what makes a re-run of a
   * seeding script free, and what keeps the history a record of changes rather than of runs.
   *
   * <p>The key is the extras grammar after the application segment. Its SHAPE is checked here; what
   * the value means is not this service's question — qits-platform-deployments' {@code
   * ServiceExtras} stays the single parser of a mount, a publish or an alias.
   */
  @PUT
  @Path("/{application}/entries/{key}")
  @Operation(summary = "Set one entry's value")
  @APIResponse(responseCode = "200", description = "The entry, already present")
  @APIResponse(responseCode = "201", description = "The entry, newly created")
  @APIResponse(responseCode = "400", description = "The application, key or value is not valid")
  @RolesAllowed({"qits:admin", "qits:system"})
  public Response set(
      @PathParam("application") String application,
      @PathParam("key") String key,
      SetEntryRequest request) {
    boolean existed = exists(application, key);
    ConfigurationEntryDto entry =
        mapper.toDto(
            configuration.upsert(
                application, key, request == null ? null : request.value(), actor()));
    return Response.status(existed ? Response.Status.OK : Response.Status.CREATED)
        .entity(new SetEntryRequest.Response(entry))
        .build();
  }

  /**
   * Remove one entry.
   *
   * <p>The value is not lost: a deleted revision is appended and the history keeps what was removed,
   * which is what makes an accidental delete answerable rather than merely regrettable.
   */
  @DELETE
  @Path("/{application}/entries/{key}")
  @Operation(summary = "Remove one entry, keeping it in the history")
  @APIResponse(responseCode = "204", description = "Removed")
  @APIResponse(responseCode = "400", description = "The application or key is not valid")
  @APIResponse(responseCode = "404", description = "No such entry")
  @RolesAllowed({"qits:admin", "qits:system"})
  public Response remove(
      @PathParam("application") String application, @PathParam("key") String key) {
    configuration.delete(application, key, actor());
    return Response.noContent().build();
  }

  /** One application's whole history, newest first. Deletions are in it, with a null value. */
  @GET
  @Path("/{application}/history")
  @Operation(summary = "One application's write history, newest first")
  @APIResponse(responseCode = "200", description = "The revisions")
  @APIResponse(responseCode = "400", description = "The application name is not valid")
  @RolesAllowed({"qits:admin", "qits:system"})
  public ListHistoryResponse history(@PathParam("application") String application) {
    return new ListHistoryResponse(
        configuration.history(application).stream().map(mapper::toDto).toList());
  }

  /**
   * Whether the key is already there, asked before the write so the answer can be 201 or 200.
   *
   * <p>It is a second read rather than a flag out of the service, and that is deliberate: the write
   * seam's job is to keep the revision and the head in step, and returning "did I create it" would
   * make the created/updated distinction part of a contract that has no other use for it. A racing
   * pair of first writes answers 201 twice, which costs a caller nothing.
   */
  private boolean exists(String application, String key) {
    try {
      configuration.require(application, key);
      return true;
    } catch (eu.wohlben.qits.configuration.error.NotFoundException absent) {
      return false;
    }
  }

  /**
   * Who to record as the writer: the resolved principal's name.
   *
   * <p>Anonymous is not a security state here — every route is {@code @RolesAllowed}, so nothing
   * unauthenticated reaches this method — it is only the case where there is no name worth
   * recording, and null is the honest answer rather than a fabricated one.
   */
  private String actor() {
    if (identity == null || identity.isAnonymous() || identity.getPrincipal() == null) {
      return null;
    }
    String name = identity.getPrincipal().getName();
    return name == null || name.isBlank() ? null : name;
  }
}
