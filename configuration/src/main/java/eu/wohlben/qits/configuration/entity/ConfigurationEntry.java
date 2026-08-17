package eu.wohlben.qits.configuration.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

/**
 * The current value of one deployment-configuration entry — the read-optimised HEAD over the
 * append-only {@link ConfigurationRevision} log.
 *
 * <p>Panache active-record with public fields, the platform's entity idiom.
 *
 * <p><b>The three field names that do not match their columns.</b> {@code entryKey}, {@code
 * entryValue} and {@code entryClass} are stored as {@code key}, {@code value} and {@code class} —
 * the spelling the extras grammar and the plan both use. The Java names differ because {@code KEY}
 * and {@code VALUE} are reserved in HQL (they are the map-entry functions) and {@code class} is a
 * Java keyword. Renaming the columns to match instead would have put the mismatch where a person
 * reads SQL by hand, which is the worse half to surprise.
 *
 * <p>No relation to any other context's entity, and there will not be one: {@link #application}
 * names an application by the string qits-platform-deployments knows it by, because that row lives
 * in another physical database.
 */
@Entity
@Table(
    name = "configuration_entry",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uq_configuration_entry_application_key",
            columnNames = {"application", "key"}))
public class ConfigurationEntry extends PanacheEntityBase {

  /** The plain entry class, and the only one v1 writes. See {@link #entryClass}. */
  public static final String CLASS_PLAIN = "plain";

  @Id public UUID id;

  /** The application this entry configures, dns-label-shaped. */
  @Column(nullable = false, length = 64)
  public String application;

  /**
   * The extras grammar after the application segment — {@code env.<VAR>}, {@code mounts[i]}, {@code
   * publishes[i]}, {@code groups[i]} or {@code aliases[i]}.
   *
   * <p>Its SHAPE is validated on the way in; what the value beside it means is not this service's
   * question. qits-platform-deployments' {@code ServiceExtras} stays the single parser.
   */
  @Column(name = "key", nullable = false, length = 256)
  public String entryKey;

  /** The value, verbatim. Never null — a deletion removes the row rather than blanking it. */
  @Column(name = "value", nullable = false, columnDefinition = "text")
  public String entryValue;

  /**
   * What kind of entry this is: {@link #CLASS_PLAIN} in v1 and nothing else. A {@code secret} class
   * is the qits-secrets fold-in and arrives with the code that can hold one — an in-memory,
   * approval-gated, one-shot credential is not a value this table may ever carry.
   */
  @Column(name = "class", nullable = false, length = 32)
  public String entryClass;

  /**
   * The {@link ConfigurationRevision#seq} this value came from, written in the same transaction as
   * that row. It is what a consumer records to say which configuration it deployed with.
   */
  @Column(name = "head_revision", nullable = false)
  public long headRevision;

  @Column(name = "updated_at", nullable = false)
  public Instant updatedAt;

  /** The principal that wrote it, or null when nothing had a name to record (a bootstrap seed). */
  @Column(name = "updated_by", length = 255)
  public String updatedBy;
}
