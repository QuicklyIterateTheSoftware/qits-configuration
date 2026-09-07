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
 * One declaration intake or removal, recorded forever.
 *
 * <p><b>It carries more weight than {@link ConfigurationRevision} does, not less.</b> An entry can
 * only ever be superseded; a declaration can be REMOVED — the delete door exists so a bad tag can be
 * re-cut — and a re-cut tag with no record of the first one would be a version that quietly meant two
 * things. This log is what keeps that answerable.
 *
 * <p><b>It is also what "governing" means.</b> Which declaration an application is currently judged
 * against is not "the newest by timestamp" and not "the highest version" — this service does not get
 * to have an opinion about version ordering, since the strings are whatever the pipeline shipped. It
 * is the newest intake that has not been taken away: walk {@link #seq} downwards, skip the deletions,
 * take the first one whose declaration is still there. That makes a rollback expressible (delete the
 * bad version and the previous one governs again) without anybody having to re-post a document that
 * has not changed.
 *
 * <p><b>An identical write appends nothing</b>, the same doctrine the entry log follows: re-posting
 * a document byte-for-byte identical to the stored one is answered 200 and leaves this table exactly
 * as it found it. A pipeline step that retries is not an event.
 */
@Entity
@Table(name = "configuration_declaration_revision")
public class ConfigurationDeclarationRevision extends PanacheEntityBase {

  /** The intake number, and an ORDER as much as an id — see the class javadoc. */
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long seq;

  @Column(nullable = false, length = 64)
  public String application;

  @Column(nullable = false, length = 128)
  public String version;

  /** The hash taken in, or null when {@link #deleted}. */
  @Column(name = "content_hash", length = 64)
  public String contentHash;

  /** True when this revision removed the declaration. */
  @Column(nullable = false)
  public boolean deleted;

  /** The machine identity behind it, or null when there was no name worth recording. */
  @Column(name = "received_by", length = 255)
  public String receivedBy;

  @Column(name = "received_at", nullable = false)
  public Instant receivedAt;
}
