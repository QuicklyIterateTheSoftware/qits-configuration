package eu.wohlben.qits.configuration.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;

/**
 * One key of one {@link ConfigurationDeclaration}, flattened: the union of what every type carries,
 * with the columns its own type does not use left null.
 *
 * <p>A table per type would be tidier in the abstract and worse in every concrete use — the reads
 * are "every key this version declares" and "who declares a version of this package", and both would
 * become a union of five queries to buy a NOT NULL nobody reads.
 *
 * <p><b>Two field names do not match their columns, the same trade {@link ConfigurationEntry}
 * makes.</b> {@link #declaredKey} is stored as {@code key} and {@link #declaredType} as {@code
 * type}: both are legal unquoted column names in PostgreSQL and both are taken in HQL — {@code KEY}
 * is the map-entry function, {@code TYPE} the polymorphic-type one. Renaming the columns instead
 * would put the mismatch where a person reads SQL by hand.
 *
 * <p><b>No hostname column, and that is the point of the whole feature.</b> A serviceAddress stores
 * the application it addresses and the port, and nothing else. The address itself differs per
 * environment and again per the addressed application's own plane, so it is rendered at the resolved
 * read; a stored one would be a single environment's answer frozen into a platform-plane table.
 */
@Entity
@Table(
    name = "configuration_declared_key",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uq_configuration_declared_key",
            columnNames = {"application", "version", "key"}))
public class ConfigurationDeclaredKey extends PanacheEntityBase {

  @Id public UUID id;

  @Column(nullable = false, length = 64)
  public String application;

  @Column(nullable = false, length = 128)
  public String version;

  /** The extras key being declared, in the same grammar a stored entry key uses. */
  @Column(name = "key", nullable = false, length = 256)
  public String declaredKey;

  /** One of {@code string}, {@code boolean}, {@code number}, {@code serviceAddress}, {@code
   * packageVersion}. */
  @Column(name = "type", nullable = false, length = 32)
  public String declaredType;

  /**
   * The declared fallback, or null when the key was declared without one.
   *
   * <p>Null and {@code ""} are different statements, and the distinction is kept for the same reason
   * {@link ConfigurationRevision} keeps a deletion apart from an empty value: "there is no default"
   * and "the default is the empty string" are answers a deployer acts on differently.
   */
  @Column(name = "default_value", columnDefinition = "text")
  public String defaultValue;

  /** serviceAddress only: the application addressed, by its deployed application name. */
  @Column(name = "service_ref", length = 64)
  public String serviceRef;

  /** serviceAddress only: the port on that application. */
  @Column(name = "service_port")
  public Integer servicePort;

  /** packageVersion only: what kind of package ({@code docker}, {@code binary}). */
  @Column(name = "package_type", length = 32)
  public String packageType;

  /** packageVersion only: the package's name ({@code qits/workspace}). */
  @Column(name = "package_name", length = 255)
  public String packageName;
}
