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
            name = "uq_configuration_entry_env_application_key",
            columnNames = {"env", "application", "key"}))
public class ConfigurationEntry extends PanacheEntityBase {

  /**
   * THE OPERATOR'S CLASS: a value a person set through the API, by hand.
   *
   * <p>It was "the only word v1 writes" and it is now the top of a precedence: a declared default
   * loses to an imported value, and an imported value loses to this. See {@link #CLASS_IMPORTED}.
   */
  public static final String CLASS_PLAIN = "plain";

  /**
   * THE BOOTSTRAP'S CLASS: a value the bulk import wrote out of the deployer's properties file.
   *
   * <p><b>The distinction is what lets the import be safe to re-run.</b> The import seeds an
   * environment from a file and runs on every boot; an operator fixes a live environment through the
   * API. With one word for both, the next boot would silently undo the fix — the failure this
   * vocabulary exists to remove. So the import writes {@code imported}, refuses to overwrite a {@code
   * plain} row, and reports how many it kept.
   *
   * <p>Rows written before this word existed are all {@code plain}, which reads as "an operator set
   * it" and protects them. That is the conservative direction to be wrong in: the cost is an import
   * that declines to update a row nobody typed, reported in the summary, rather than an edit
   * silently reverted.
   */
  public static final String CLASS_IMPORTED = "imported";

  @Id public UUID id;

  /**
   * The environment this entry belongs to, dns-label-shaped like {@link #application}.
   *
   * <p><b>It is part of the entry's identity, not a label on it.</b> This service runs on the
   * platform plane and holds every environment's configuration in one store, so {@code (env,
   * application, key)} is what has one current value — the unique constraint above says so, and
   * every read and every write on this table names an env. What was implicit in a per-tier
   * deployment is explicit in the row.
   */
  @Column(nullable = false, length = 64)
  public String env;

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
   * What kind of entry this is, and WHO WROTE IT: {@link #CLASS_PLAIN} for an operator, {@link
   * #CLASS_IMPORTED} for the bootstrap's file.
   *
   * <p><b>It is the writer's own word.</b> {@code ConfigurationService.store} takes the class from
   * its caller rather than deciding one, because the door is not in a position to know whether the
   * request behind it is a person fixing an environment or a script replaying a file — and a store
   * that guessed would be a store whose precedence rule rested on a guess.
   *
   * <p>A {@code secret} class is the qits-secrets fold-in and arrives with the code that can hold one
   * — an in-memory, approval-gated, one-shot credential is not a value this table may ever carry.
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
