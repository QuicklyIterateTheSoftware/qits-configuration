package eu.wohlben.qits.configuration.persistence;

import eu.wohlben.qits.configuration.entity.ConfigurationDeclaration;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The declaration documents, one per (application, version).
 *
 * <p>Method names avoid Panache's own ({@code find}, {@code list}) wherever the arity would make the
 * overload a coin toss to read — the same rule {@link ConfigurationEntryRepository} states. {@link
 * #find(String, String)} keeps the plain name because two string arguments cannot be confused with
 * the inherited varargs query.
 */
@ApplicationScoped
public class ConfigurationDeclarationRepository
    implements PanacheRepositoryBase<ConfigurationDeclaration, UUID> {

  /** The one document under a version, or empty. */
  public Optional<ConfigurationDeclaration> find(String application, String version) {
    return find("application = ?1 and version = ?2", application, version).firstResultOptional();
  }

  /**
   * THE GOVERNING DECLARATION: the one an application is currently judged against, or empty when it
   * has never declared or has had every version taken away.
   *
   * <p><b>It is decided by the audit log, not by the documents.</b> The alternatives were both
   * wrong: newest-by-timestamp says nothing about intent when a pipeline re-posts an old tag, and
   * highest-by-version would need this service to have an opinion about how version strings order —
   * which it deliberately does not have, since the strings are whatever qits-ci shipped and a
   * calver, a semver and a git tag do not compare.
   *
   * <p>So it walks the intake log downwards and takes the first row that is not a deletion AND still
   * names a live document. That second half is what makes the delete door a ROLLBACK rather than a
   * blanking: delete a bad version and the one before it governs again, with nobody re-posting a
   * document that has not changed. It is one query because the join is what expresses "still names a
   * live document" — a loop over revisions doing a lookup each time would be the same answer at one
   * round trip per revision ever recorded.
   */
  public Optional<ConfigurationDeclaration> governingOf(String application) {
    return getEntityManager()
        .createQuery(
            "select d from ConfigurationDeclaration d, ConfigurationDeclarationRevision r"
                + " where d.application = :application"
                + " and r.application = :application"
                + " and r.version = d.version"
                + " and r.deleted = false"
                + " order by r.seq desc",
            ConfigurationDeclaration.class)
        .setParameter("application", application)
        .setMaxResults(1)
        .getResultStream()
        .findFirst();
  }

  /**
   * Every version one application has declared, newest intake first.
   *
   * <p>Ordered by {@code received_at} then {@code version} rather than by version alone, for the
   * reason above: this service has no version ordering. What it does have is when each document
   * arrived, and the tiebreak keeps two documents received in the same microsecond from swapping
   * places between two reads of the same listing.
   */
  public List<ConfigurationDeclaration> listByApplication(String application) {
    return list("application = ?1 order by receivedAt desc, version desc", application);
  }

  /** Every declaration this store holds, application first. */
  public List<ConfigurationDeclaration> listEverything() {
    return list("order by application, receivedAt desc, version desc");
  }
}
