package eu.wohlben.qits.configuration.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * One write, recorded forever. The append-only half of this context: every upsert that changes a
 * value and every delete adds exactly one row here, in the same transaction that moves the head.
 *
 * <p><b>Nothing updates or removes a revision.</b> The log is the authority — {@link
 * ConfigurationEntry} is reproducible from it — which is what makes an accidental edit answerable
 * rather than merely regrettable.
 *
 * <p><b>An identical write appends nothing.</b> That is the idempotency the import path rests on: a
 * bootstrap that re-imports the same properties file leaves the log exactly as it found it, so the
 * history stays a record of changes rather than of runs.
 */
@Entity
@Table(name = "configuration_revision")
public class ConfigurationRevision extends PanacheEntityBase {

  /**
   * The revision number, and an ORDER as much as an id: "newest first" and "which revision did the
   * deployer read" are both statements about this value, and a random id could express neither.
   */
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long seq;

  @Column(nullable = false, length = 64)
  public String application;

  /** See {@link ConfigurationEntry#entryKey} for why the field and the column differ. */
  @Column(name = "key", nullable = false, length = 256)
  public String entryKey;

  /**
   * The value written, or null when {@link #deleted}.
   *
   * <p>Two columns rather than one: a deletion is not the empty string. An entry may legitimately
   * hold {@code ""}, and a sentinel would make the two indistinguishable the first time somebody
   * wanted an empty variable.
   */
  @Column(name = "value", columnDefinition = "text")
  public String entryValue;

  /** True when this revision removed the entry. */
  @Column(nullable = false)
  public boolean deleted;

  /** The principal that wrote it, or null when nothing had a name to record. */
  @Column(name = "updated_by", length = 255)
  public String updatedBy;

  @Column(name = "updated_at", nullable = false)
  public Instant updatedAt;
}
