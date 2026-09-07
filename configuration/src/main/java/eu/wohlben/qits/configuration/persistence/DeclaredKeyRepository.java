package eu.wohlben.qits.configuration.persistence;

import eu.wohlben.qits.configuration.entity.ConfigurationDeclaredKey;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.UUID;

/** The parsed keys of the declaration documents, one row per (application, version, key). */
@ApplicationScoped
public class DeclaredKeyRepository
    implements PanacheRepositoryBase<ConfigurationDeclaredKey, UUID> {

  /** Every key one version declares, by key — the order a resolved read merges them in. */
  public List<ConfigurationDeclaredKey> listOf(String application, String version) {
    return list(
        "application = ?1 and version = ?2 order by declaredKey", application, version);
  }

  /**
   * THE REVERSE QUESTION: every declared key that carries a version of one package.
   *
   * <p>It is the question qits-artifacts' collector asks — "who runs a version of {@code
   * qits/workspace}, so which tags may I not delete" — and the reason this table has an index on the
   * pair. Today that answer comes from {@code control/ImagePins}, a hand-maintained list of four
   * mappings that a fifth consumer joins by somebody remembering to edit it. This method is the same
   * answer derived from what the applications themselves declared, for every application at once.
   *
   * <p>It is not wired to the pin report yet, deliberately: {@code /pins} and {@code
   * bus/SoftwareReleaseListener} are generalised in their own wave, and switching the report's source
   * underneath a consumer that decides what to DELETE is not a change to make as a side effect of
   * landing the store.
   */
  public List<ConfigurationDeclaredKey> listByPackage(String packageType, String packageName) {
    return list(
        "packageType = ?1 and packageName = ?2 order by application, version, declaredKey",
        packageType,
        packageName);
  }

  /** Remove every key of one version, as its declaration is removed. */
  public long deleteOf(String application, String version) {
    return delete("application = ?1 and version = ?2", application, version);
  }
}
