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
 * One version of one application's declaration document — {@code .config/qits/configuration.yml} as
 * the pipeline posted it.
 *
 * <p><b>This is the second document class this context holds, and the only one it parses.</b> A
 * {@link ConfigurationEntry} is a value somebody stored and this service has never read one. A
 * declaration is a statement about what the keys ARE, and serving a typed read means understanding
 * it — see {@code control/DeclarationParser} for why that parser can only live here.
 *
 * <p><b>{@link #raw} is kept beside the parsed {@link ConfigurationDeclaredKey} rows on purpose.</b>
 * The parsed rows are what this service answers with; the raw text is what the application actually
 * committed, and it is the only thing that can settle an argument about whether a version declared
 * what somebody remembers it declaring. {@link #contentHash} is over exactly those bytes, so "the
 * same document" is a question about the file rather than about this parser's opinion of it.
 *
 * <p><b>{@link #deploymentTarget} arrives with the SEED, never out of the document.</b> Which plane
 * an application deploys onto is qits-platform-deployments' fact, spelled {@code deployment_target}
 * in that application's own {@code .config/qits/deployments.yml}; the pipeline posting the
 * declaration knows it and passes it. It is what decides whether a peer's serviceAddress renders as
 * {@code qits-events} or as {@code dev-qits-events}, so a document that asserted its own plane would
 * be a second answer to a question that already has one — and the day a service is promoted, the two
 * would disagree with a container dialling the void as the outcome.
 */
@Entity
@Table(
    name = "configuration_declaration",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uq_configuration_declaration_application_version",
            columnNames = {"application", "version"}))
public class ConfigurationDeclaration extends PanacheEntityBase {

  @Id public UUID id;

  /** The application this document belongs to, dns-label-shaped and carrying the {@code qits-}
   * prefix exactly as its deployment does. */
  @Column(nullable = false, length = 64)
  public String application;

  /** The version it was published under — a release version or a tag, not a name this service
   * invents. */
  @Column(nullable = false, length = 128)
  public String version;

  /** {@code platform} or {@code environment}; see the class javadoc for why it is not in the file. */
  @Column(name = "deployment_target", nullable = false, length = 32)
  public String deploymentTarget;

  /** SHA-256 of {@link #raw}, hex. A second post of the same bytes is a no-op; of different bytes, a
   * conflict. */
  @Column(name = "content_hash", nullable = false, length = 64)
  public String contentHash;

  /** The document verbatim, comments and descriptions included. */
  @Column(nullable = false, columnDefinition = "text")
  public String raw;

  @Column(name = "received_at", nullable = false)
  public Instant receivedAt;

  /** The machine identity that posted it, or null when there was no name worth recording. */
  @Column(name = "received_by", length = 255)
  public String receivedBy;
}
