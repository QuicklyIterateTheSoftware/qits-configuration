package eu.wohlben.qits.configuration.persistence;

import eu.wohlben.qits.configuration.entity.ConfigurationDeclarationRevision;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * The declaration intake log. Append-only: nothing here updates or removes a row, and nothing should
 * — this is what decides which declaration governs an application, and a log that could be edited
 * would make "which document was this deployment resolved against" unanswerable.
 */
@ApplicationScoped
public class DeclarationRevisionRepository
    implements PanacheRepositoryBase<ConfigurationDeclarationRevision, Long> {

  /**
   * Record one intake or one removal, and hand back the row.
   *
   * <p>Flushed here rather than at the end of the caller's transaction, for the reason the entry log
   * gives: the seq is an identity column and has no value until the insert has run, and a caller
   * that wants to report what it recorded would otherwise be holding a null.
   */
  public ConfigurationDeclarationRevision append(
      String application, String version, String contentHash, boolean deleted, String actor) {
    ConfigurationDeclarationRevision revision = new ConfigurationDeclarationRevision();
    revision.application = application;
    revision.version = version;
    revision.contentHash = contentHash;
    revision.deleted = deleted;
    revision.receivedBy = actor;
    // Truncated to the column's own precision, so a value read back equals the one written rather
    // than differing in digits the database never kept.
    revision.receivedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    persist(revision);
    flush();
    return revision;
  }

  /** One application's intake history, newest first. */
  public List<ConfigurationDeclarationRevision> listByApplication(String application) {
    return list("application = ?1 order by seq desc", application);
  }
}
