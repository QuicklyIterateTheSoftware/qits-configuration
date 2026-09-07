package eu.wohlben.qits.configuration.control;

import eu.wohlben.qits.configuration.dto.DeclarationDto;
import eu.wohlben.qits.configuration.dto.DeclarationSummaryDto;
import eu.wohlben.qits.configuration.dto.DeclaredKeyDto;
import eu.wohlben.qits.configuration.entity.ConfigurationDeclaration;
import eu.wohlben.qits.configuration.entity.ConfigurationDeclaredKey;
import eu.wohlben.qits.configuration.error.ConflictException;
import eu.wohlben.qits.configuration.error.NotFoundException;
import eu.wohlben.qits.configuration.persistence.ConfigurationDeclarationRepository;
import eu.wohlben.qits.configuration.persistence.DeclarationRevisionRepository;
import eu.wohlben.qits.configuration.persistence.DeclaredKeyRepository;
import eu.wohlben.qits.db.DbRetry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The declaration store: take in an application's {@code .config/qits/configuration.yml}, keep every
 * version of it, and say which one governs.
 *
 * <p><b>Three answers to a POST and no fourth.</b> A document that will not parse is refused (422),
 * naming the key. A document byte-for-byte identical to the one already stored under that version is
 * a no-op (200) that appends NOTHING — the same doctrine {@code ConfigurationService.store} follows,
 * and the reason a pipeline step is free to retry. A DIFFERENT document under a version already
 * taken is a conflict (409) naming both hashes, because a version is a fixed point: two builds
 * disagreeing about what {@code qits-ci@2026.907.1} declared is not an update to apply, it is a
 * question somebody has to answer. Anything else is a new version, recorded (201).
 *
 * <p><b>The delete door exists for exactly one thing: a re-cut tag.</b> A build that published a
 * declaration and was then rebuilt under the same version has no other way out — the conflict above
 * is doing its job and will keep doing it. So removal is deliberate, machine-only, and logged: the
 * document and its keys go, a deleted revision stays, and the previously-governing version governs
 * again. What it is NOT is a cleanup path. Nothing sweeps old declarations, and nothing should: a
 * deployment that resolved against a version is answerable only while that version is still here.
 *
 * <p><b>The write brackets are {@link DbRetry#inNewTx} and each body ends with a flush</b>, the
 * house rule for every write in this context — see {@code ConfigurationService} for why the flush is
 * what keeps a lost connection on the decidable side of a retry. Reads are deliberately not wrapped.
 */
@ApplicationScoped
public class DeclarationService {

  @Inject ConfigurationDeclarationRepository declarations;

  @Inject DeclaredKeyRepository declaredKeys;

  @Inject DeclarationRevisionRepository revisions;

  /**
   * What an intake did, so the boundary can answer 201 or 200 without asking the store a second
   * question.
   *
   * <p>Unlike the entry PUT — which deliberately re-reads rather than have the write seam report
   * created-or-not — this one is returned, because here the distinction is not cosmetic: 200 means
   * "this exact document was already recorded" and 201 means "it is recorded now", and a caller
   * pipeline branches on it.
   */
  public record Intake(boolean created, ConfigurationDeclaration declaration) {}

  // ---------------------------------------------------------------- writes

  /**
   * Take one declaration document in under {@code (application, version)}.
   *
   * @param deploymentTarget the plane the application deploys onto, from the deployer and not from
   *     the document — see {@link ConfigurationKeys#requireDeploymentTarget}
   */
  public Intake declare(
      String application, String version, String deploymentTarget, String raw, String actor) {
    String target = ConfigurationKeys.requireDeploymentTarget(deploymentTarget);
    // Parsed BEFORE the transaction opens. A document that will not parse is a 422 that touches no
    // row, and holding a connection open while a YAML loader runs would be paying for the refusal.
    DeclarationParser.Declaration parsed = DeclarationParser.parse(application, version, raw);

    Optional<ConfigurationDeclaration> existing =
        declarations.find(parsed.application(), parsed.version());
    if (existing.isPresent()) {
      ConfigurationDeclaration stored = existing.get();
      if (!stored.contentHash.equals(parsed.contentHash())) {
        throw new ConflictException(
            "Declaration "
                + parsed.application()
                + "@"
                + parsed.version()
                + " is already stored with content hash "
                + stored.contentHash
                + " and this document hashes to "
                + parsed.contentHash()
                + ". A version names one document; re-cut the tag or post a new version.");
      }
      // AN IDENTICAL WRITE APPENDS NOTHING — not a revision, and not a re-attribution either. A
      // pipeline that retries a step is not an event, and the intake log is a record of what was
      // declared rather than of how many times a runner tried to say it.
      return new Intake(false, stored);
    }

    return new Intake(
        true,
        DbRetry.inNewTx(
            "declare " + parsed.application() + "@" + parsed.version(),
            () -> {
              ConfigurationDeclaration declaration = new ConfigurationDeclaration();
              declaration.id = UUID.randomUUID();
              declaration.application = parsed.application();
              declaration.version = parsed.version();
              declaration.deploymentTarget = target;
              declaration.contentHash = parsed.contentHash();
              declaration.raw = parsed.raw();
              declaration.receivedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
              declaration.receivedBy = actor;
              declarations.persist(declaration);

              for (DeclarationParser.DeclaredKey declared : parsed.keys()) {
                ConfigurationDeclaredKey key = new ConfigurationDeclaredKey();
                key.id = UUID.randomUUID();
                key.application = declaration.application;
                key.version = declaration.version;
                key.declaredKey = declared.key();
                key.declaredType = declared.type();
                key.defaultValue = declared.defaultValue();
                key.serviceRef = declared.serviceRef();
                key.servicePort = declared.servicePort();
                key.packageType = declared.packageType();
                key.packageName = declared.packageName();
                declaredKeys.persist(key);
              }

              revisions.append(
                  declaration.application,
                  declaration.version,
                  declaration.contentHash,
                  false,
                  actor);
              declarations.flush();
              return declaration;
            }));
  }

  /**
   * Remove one declaration and its keys, keeping the record that it was here.
   *
   * <p>Removal is what makes {@link ConfigurationDeclarationRepository#governingOf} a rollback:
   * afterwards the newest surviving intake governs again, and nobody has to re-post a document that
   * has not changed.
   */
  public void remove(String application, String version, String actor) {
    String app = ConfigurationKeys.requireApplication(application);
    String tag = ConfigurationKeys.requireDeclarationVersion(version);
    DbRetry.runInNewTx(
        "remove declaration " + app + "@" + tag,
        () -> {
          ConfigurationDeclaration existing =
              declarations
                  .find(app, tag)
                  .orElseThrow(
                      () ->
                          new NotFoundException(
                              "No declaration " + tag + " for application " + app));
          declaredKeys.deleteOf(app, tag);
          declarations.delete(existing);
          revisions.append(app, tag, null, true, actor);
          declarations.flush();
        });
  }

  // ---------------------------------------------------------------- reads

  /** Every version one application has declared, newest intake first, with the governing one flagged. */
  public List<DeclarationSummaryDto> declarationsOf(String application) {
    String app = ConfigurationKeys.requireApplication(application);
    String governing = declarations.governingOf(app).map(each -> each.version).orElse(null);
    List<ConfigurationDeclaration> stored = declarations.listByApplication(app);
    List<DeclarationSummaryDto> summaries = new ArrayList<>(stored.size());
    for (ConfigurationDeclaration declaration : stored) {
      summaries.add(
          new DeclarationSummaryDto(
              declaration.application,
              declaration.version,
              declaration.deploymentTarget,
              declaration.contentHash,
              declaredKeys.listOf(declaration.application, declaration.version).size(),
              declaration.version.equals(governing),
              declaration.receivedAt,
              declaration.receivedBy));
    }
    return summaries;
  }

  /** One declaration in full — parsed keys and the document — or a 404 naming it. */
  public DeclarationDto declaration(String application, String version) {
    String app = ConfigurationKeys.requireApplication(application);
    String tag = ConfigurationKeys.requireDeclarationVersion(version);
    ConfigurationDeclaration declaration =
        declarations
            .find(app, tag)
            .orElseThrow(
                () -> new NotFoundException("No declaration " + tag + " for application " + app));
    boolean governing =
        declarations.governingOf(app).map(each -> tag.equals(each.version)).orElse(false);
    List<DeclaredKeyDto> keys =
        declaredKeys.listOf(app, tag).stream()
            .map(
                key ->
                    new DeclaredKeyDto(
                        key.declaredKey,
                        key.declaredType,
                        key.defaultValue,
                        key.serviceRef,
                        key.servicePort,
                        key.packageType,
                        key.packageName))
            .toList();
    return new DeclarationDto(
        declaration.application,
        declaration.version,
        declaration.deploymentTarget,
        declaration.contentHash,
        governing,
        declaration.receivedAt,
        declaration.receivedBy,
        keys,
        declaration.raw);
  }
}
