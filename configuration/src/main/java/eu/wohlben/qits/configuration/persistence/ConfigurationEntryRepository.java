package eu.wohlben.qits.configuration.persistence;

import eu.wohlben.qits.configuration.entity.ConfigurationEntry;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The head rows: one per (application, key) that currently has a value.
 *
 * <p>Ordering is {@code (application, key)} everywhere a list is returned. A resolved read is
 * layered into somebody else's configuration, where order means nothing, but a person diffing two
 * environments reads these lists side by side — and an unordered listing makes that diff noise.
 *
 * <p>The method names avoid Panache's own ({@code find}, {@code listAll}): an overload that differs
 * from an inherited varargs method only in arity is a coin toss to read, and this repository's
 * callers should never have to check which one they got.
 */
@ApplicationScoped
public class ConfigurationEntryRepository
    implements PanacheRepositoryBase<ConfigurationEntry, UUID> {

  /** The one current row for a key, or empty. */
  public Optional<ConfigurationEntry> findEntry(String application, String key) {
    return find("application = ?1 and entryKey = ?2", application, key).firstResultOptional();
  }

  /** Every current entry of one application, by key. */
  public List<ConfigurationEntry> listByApplication(String application) {
    return list("application = ?1 order by entryKey", application);
  }

  /** Every current entry, application first. */
  public List<ConfigurationEntry> listEverything() {
    return listAll(Sort.by("application").and("entryKey"));
  }
}
