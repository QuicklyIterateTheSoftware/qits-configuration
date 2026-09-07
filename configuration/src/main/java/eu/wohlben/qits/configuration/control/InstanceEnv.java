package eu.wohlben.qits.configuration.control;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * The env this instance's pre-platform rows belong to, and the env its env-less callers mean.
 *
 * <p><b>One property, two uses, and that is the whole point of this class.</b> {@code
 * qits.configuration.legacy-env} (env {@code QITS_CONFIGURATION_LEGACY_ENV}) is what V2's backfill
 * stamps into every inherited row, and it is what the transitional env-less API routes pass down
 * when a caller that predates the plane flip asks for "the" configuration of an application. Those
 * two answers MUST be the same string: a backfill that said {@code dev} while the legacy routes read
 * {@code prod} would make every old caller look at an empty store and every new one look at rows it
 * did not write. Reading the property in two places would have permitted exactly that, so it is read
 * here and nowhere else.
 *
 * <p><b>It has no default</b>, deliberately — the refuse-to-boot stance this context takes with the
 * {@code QITS_RESOURCE_DB_*} triple. The reasoning is in {@code
 * META-INF/microprofile-config.properties} beside the placeholder wiring; the short of it is that a
 * guess would be stamped irreversibly into the log of every application.
 *
 * <p><b>This is transitional and is meant to die.</b> When the cutover feature removes the env-less
 * routes, the only remaining reader is the migration's placeholder, which is a deploy-time value and
 * not a runtime one — at which point this bean goes with them.
 */
@ApplicationScoped
public class InstanceEnv {

  @ConfigProperty(name = "qits.configuration.legacy-env")
  String configured;

  /**
   * The configured env. Never null in a running process: an unresolvable expression fails the boot
   * long before anything calls this.
   */
  public String legacyEnv() {
    return configured;
  }
}
