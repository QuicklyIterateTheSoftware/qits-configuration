package eu.wohlben.qits.configuration.persistence;

import eu.wohlben.qits.configuration.entity.ConfigurationRevision;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

/**
 * The append-only log. Nothing here updates or deletes a row, and nothing should: the log is what
 * makes the current state reproducible and an accidental edit answerable.
 */
@ApplicationScoped
public class ConfigurationRevisionRepository
    implements PanacheRepositoryBase<ConfigurationRevision, Long> {

  /** One application's history, newest first. */
  public List<ConfigurationRevision> listByApplication(String application) {
    return list("application = ?1 order by seq desc", application);
  }

  /**
   * The newest revision seq for one application, or 0 when it has none.
   *
   * <p>It is read from the LOG rather than from the entries' {@code head_revision}, and the
   * difference is a delete: removing an entry appends a revision and takes its head row away, so a
   * maximum over the heads would move BACKWARDS on a delete. A consumer records this number to say
   * which configuration it deployed with, and a number that can go back is not one.
   */
  public long headRevisionOf(String application) {
    Long max =
        getEntityManager()
            .createQuery(
                "select max(r.seq) from ConfigurationRevision r where r.application = :application",
                Long.class)
            .setParameter("application", application)
            .getSingleResult();
    return max == null ? 0L : max;
  }
}
