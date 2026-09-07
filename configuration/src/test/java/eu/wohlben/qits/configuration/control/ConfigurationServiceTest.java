package eu.wohlben.qits.configuration.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.wohlben.qits.configuration.dto.ApplicationEnvSummaryDto;
import eu.wohlben.qits.configuration.dto.ApplicationSummaryDto;
import eu.wohlben.qits.configuration.dto.ImagePinDto;
import eu.wohlben.qits.configuration.dto.ImportSummaryDto;
import eu.wohlben.qits.configuration.dto.ResolvedConfigurationDto;
import eu.wohlben.qits.configuration.entity.ConfigurationEntry;
import eu.wohlben.qits.configuration.entity.ConfigurationRevision;
import eu.wohlben.qits.configuration.error.BadRequestException;
import eu.wohlben.qits.configuration.error.NotFoundException;
import eu.wohlben.qits.configuration.persistence.ConfigurationEntryRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The write seam, against a real PostgreSQL — embedded, spawned from a Maven artifact, never a
 * container.
 *
 * <p>Every test uses an application name of its own. The suite shares one database across classes
 * (Flyway cleans at start, not between tests), so a shared name would make one test's rows another
 * test's surprise.
 *
 * <p><b>Every call names an env, because every method does.</b> {@link #ENV} is the suite's own,
 * matching {@code qits.configuration.legacy-env} in this module's test properties — so the rows these
 * tests write sit in the same env the V2 backfill would have stamped, and a value read back under
 * another env is the thing {@link #anEnvIsPartOfTheIdentityOfAnEntry} refuses.
 */
@QuarkusTest
class ConfigurationServiceTest {

  /** The env this suite writes in — the one the test properties configure as the legacy env. */
  private static final String ENV = "test";

  /** A second env, used only to prove that the first one's rows are not visible from it. */
  private static final String OTHER_ENV = "other";

  @Inject ConfigurationService configuration;

  @Inject ConfigurationEntryRepository entries;

  @Test
  void aFirstWriteCreatesTheEntryAndOneRevision() {
    ConfigurationEntry entry =
        configuration.upsert(ENV, "app-first", "env.QITS_REGISTRY", "localhost:8081", "alice");

    assertEquals("app-first", entry.application);
    assertEquals("env.QITS_REGISTRY", entry.entryKey);
    assertEquals("localhost:8081", entry.entryValue);
    assertEquals(ConfigurationEntry.CLASS_PLAIN, entry.entryClass);
    assertEquals("alice", entry.updatedBy);

    List<ConfigurationRevision> history = configuration.history(ENV, "app-first");
    assertEquals(1, history.size());
    assertEquals("localhost:8081", history.get(0).entryValue);
    assertFalse(history.get(0).deleted);
    assertEquals(entry.headRevision, history.get(0).seq);
  }

  @Test
  void anIdenticalValueWritesNoRevision() {
    configuration.upsert(ENV, "app-idempotent", "env.A", "one", "alice");
    long afterFirst = configuration.resolve(ENV, "app-idempotent").headRevision();

    configuration.upsert(ENV, "app-idempotent", "env.A", "one", "bob");

    assertEquals(1, configuration.history(ENV, "app-idempotent").size());
    assertEquals(
        afterFirst,
        configuration.resolve(ENV, "app-idempotent").headRevision(),
        "an identical write must not move the head revision");
    assertEquals(
        "alice",
        configuration.require(ENV, "app-idempotent", "env.A").updatedBy,
        "an identical write must not re-attribute the entry either");
  }

  @Test
  void aChangedValueAppendsAndMovesTheHead() {
    ConfigurationEntry first = configuration.upsert(ENV, "app-change", "env.A", "one", "alice");
    ConfigurationEntry second = configuration.upsert(ENV, "app-change", "env.A", "two", "bob");

    assertTrue(second.headRevision > first.headRevision);
    assertEquals("two", second.entryValue);
    assertEquals("bob", second.updatedBy);

    List<ConfigurationRevision> history = configuration.history(ENV, "app-change");
    assertEquals(2, history.size(), "history is newest first");
    assertEquals("two", history.get(0).entryValue);
    assertEquals("one", history.get(1).entryValue);
  }

  @Test
  void aDeleteRemovesTheEntryAndKeepsTheHistory() {
    configuration.upsert(ENV, "app-delete", "env.A", "one", "alice");
    long beforeDelete = configuration.resolve(ENV, "app-delete").headRevision();

    configuration.delete(ENV, "app-delete", "env.A", "bob");

    assertThrows(NotFoundException.class, () -> configuration.require(ENV, "app-delete", "env.A"));
    assertTrue(configuration.entriesOf(ENV, "app-delete").isEmpty());

    List<ConfigurationRevision> history = configuration.history(ENV, "app-delete");
    assertEquals(2, history.size());
    assertTrue(history.get(0).deleted);
    assertNull(history.get(0).entryValue, "a deletion records no value; the previous one is above");
    assertEquals("bob", history.get(0).updatedBy);
    assertTrue(
        configuration.resolve(ENV, "app-delete").headRevision() > beforeDelete,
        "the head revision moves FORWARD on a delete — it comes from the log, not from the entries");
  }

  @Test
  void deletingWhatIsNotThereIsA404() {
    assertThrows(
        NotFoundException.class, () -> configuration.delete(ENV, "app-absent", "env.A", "alice"));
  }

  @Test
  void aResolvedReadIsTheFullyPrefixedPropertyMap() {
    configuration.upsert(ENV, "app-resolve", "env.QITS_A", "one", "alice");
    configuration.upsert(ENV, "app-resolve", "mounts[0]", "/data:/data", "alice");

    ResolvedConfigurationDto resolved = configuration.resolve(ENV, "app-resolve");

    assertEquals(2, resolved.properties().size());
    assertEquals(
        "one",
        resolved.properties().get("qits.platform.deployments.extras.app-resolve.env.QITS_A"));
    assertEquals(
        "/data:/data",
        resolved.properties().get("qits.platform.deployments.extras.app-resolve.mounts[0]"));
    assertTrue(resolved.headRevision() > 0);
  }

  @Test
  void anApplicationWithNothingStoredResolvesEmptyRatherThanFailing() {
    ResolvedConfigurationDto resolved = configuration.resolve(ENV, "app-unconfigured");

    assertEquals(0, resolved.headRevision());
    assertTrue(resolved.properties().isEmpty());
  }

  @Test
  void theListingKeepsAnApplicationWhoseEntriesHaveAllBeenDeleted() {
    configuration.upsert(ENV, "app-emptied", "env.A", "one", "alice");
    configuration.delete(ENV, "app-emptied", "env.A", "alice");

    ApplicationSummaryDto summary =
        configuration.applications().stream()
            .filter(each -> each.application().equals("app-emptied"))
            .findFirst()
            .orElseThrow();

    ApplicationEnvSummaryDto perEnv =
        summary.envs().stream()
            .filter(each -> each.env().equals(ENV))
            .findFirst()
            .orElseThrow(
                () ->
                    new AssertionError(
                        "the env the entries were deleted from must still be a row: the log is what"
                            + " the listing is built from"));

    assertEquals(0, perEnv.entries());
    assertTrue(perEnv.headRevision() > 0, "the history is still there and still says so");
  }

  /**
   * The claim the whole platform promotion rests on: two envs of one application are two entries,
   * two histories and two head revisions, and neither is visible from the other.
   *
   * <p>Written as a WRITE-THEN-MISS rather than as two writes compared, because the failure this
   * guards against is a query that forgot its env predicate — and such a query would pass any test
   * that only ever asserts what it just wrote.
   */
  @Test
  void anEnvIsPartOfTheIdentityOfAnEntry() {
    configuration.upsert(ENV, "app-two-envs", "env.A", "from-test", "alice");

    assertThrows(
        NotFoundException.class,
        () -> configuration.require(OTHER_ENV, "app-two-envs", "env.A"),
        "a value written in one env must not be readable from another");
    assertTrue(configuration.entriesOf(OTHER_ENV, "app-two-envs").isEmpty());
    assertEquals(
        0,
        configuration.resolve(OTHER_ENV, "app-two-envs").headRevision(),
        "an env with no history of this application is at revision 0, not at the other env's head");

    configuration.upsert(OTHER_ENV, "app-two-envs", "env.A", "from-other", "alice");

    assertEquals("from-test", configuration.require(ENV, "app-two-envs", "env.A").entryValue);
    assertEquals("from-other", configuration.require(OTHER_ENV, "app-two-envs", "env.A").entryValue);
    assertEquals(
        1,
        configuration.history(ENV, "app-two-envs").size(),
        "the second env's write is not in the first env's history");

    ApplicationSummaryDto summary =
        configuration.applications().stream()
            .filter(each -> each.application().equals("app-two-envs"))
            .findFirst()
            .orElseThrow();
    assertEquals(
        List.of(ENV, OTHER_ENV).stream().sorted().toList(),
        summary.envs().stream().map(ApplicationEnvSummaryDto::env).toList(),
        "the listing shows both envs of one application, sorted");
  }

  /**
   * The env vocabulary is the union of both tables, so an env that has only ever been deleted out of
   * still counts. That is the same doctrine the application listing follows, one level up, and it is
   * asserted here because the repository method is the only place it is implemented.
   */
  @Test
  void theEnvListingKeepsAnEnvWhoseEntriesHaveAllBeenDeleted() {
    configuration.upsert("gone", "app-env-gone", "env.A", "one", "alice");
    configuration.delete("gone", "app-env-gone", "env.A", "alice");

    assertTrue(
        entries.listDistinctEnvs().contains("gone"),
        "an env with no head rows left still has a history and is still an env");
    assertTrue(entries.listDistinctEnvs().contains(ENV));
  }

  @Test
  void theEnvGrammarIsEnforcedOnTheWritePath() {
    assertThrows(
        BadRequestException.class,
        () -> configuration.upsert("Not_An_Env", "app-env-guard", "env.A", "x", null));
    assertThrows(
        BadRequestException.class,
        () -> configuration.resolve("", "app-env-guard"),
        "a blank env is refused rather than silently meaning the legacy one — that defaulting lives"
            + " at the API boundary, which is where it can be deleted");
  }

  @Test
  void anImportWritesTheLinesItRecognisesAndCountsTheRest() {
    ImportSummaryDto summary =
        configuration.importProperties(ENV, 
            """
            # the deployer's config volume
            qits.platform.deployments.orchestrator=swarm
            qits.platform.deployments.extras.app-import.env.QITS_A=one
            qits.platform.deployments.extras.app-import.aliases[0]=app.dev.localhost
            """,
            "alice");

    assertEquals(2, summary.imported());
    assertEquals(0, summary.unchanged());
    assertEquals(2, summary.ignored(), "the comment and the deployer's own unrelated key");
    assertEquals(2, configuration.entriesOf(ENV, "app-import").size());
  }

  @Test
  void reImportingTheSameFileWritesNothingAtAll() {
    String file =
        """
        qits.platform.deployments.extras.app-reimport.env.QITS_A=one
        qits.platform.deployments.extras.app-reimport.env.QITS_B=two
        """;
    configuration.importProperties(ENV, file, "alice");
    long afterFirst = configuration.resolve(ENV, "app-reimport").headRevision();

    ImportSummaryDto again = configuration.importProperties(ENV, file, "alice");

    assertEquals(0, again.imported());
    assertEquals(2, again.unchanged());
    assertEquals(
        afterFirst,
        configuration.resolve(ENV, "app-reimport").headRevision(),
        "an unchanged import leaves the log exactly as it found it");
    assertEquals(2, configuration.history(ENV, "app-reimport").size());
  }

  @Test
  void aMalformedLineLeavesTheWholeImportUnwritten() {
    assertThrows(
        BadRequestException.class,
        () ->
            configuration.importProperties(ENV, 
                """
                qits.platform.deployments.extras.app-atomic.env.QITS_A=one
                qits.platform.deployments.extras.app-atomic.volumes[0]=nope
                """,
                "alice"));

    assertTrue(
        configuration.entriesOf(ENV, "app-atomic").isEmpty(),
        "the good line ahead of the bad one must not have survived");
  }

  /**
   * The pin report, walked in one method on purpose: "nothing pinned is an empty answer" is a claim
   * about a store no pin has been written into, and this suite shares one database across classes —
   * so it is asserted before this test writes rather than from a second method that might run after
   * it.
   *
   * <p>The application names here are the platform's real ones, because the map is a compile-time
   * constant and there is no pin on an invented application to write. Nothing else in this module
   * touches them.
   */
  @Test
  void thePinReportAnswersWhatIsStoredAndOmitsWhatWasNeverReleased() {
    assertTrue(
        configuration.imagePins().isEmpty(),
        "an environment that has released nothing pins nothing — not four rows with no version");

    configuration.upsert(ENV, 
        "qits-projects", "env.QITS_PROJECTS_AGENT_IMAGE_VERSION", "2026.904.160152", "alice");
    configuration.upsert(ENV, 
        "qits-workspaces", "env.QITS_WORKSPACE_IMAGE_VERSION", "2026.904.160522", "alice");

    assertEquals(
        List.of(
            new ImagePinDto(
                "qits/project-agent",
                "2026.904.160152",
                "qits-projects",
                "env.QITS_PROJECTS_AGENT_IMAGE_VERSION"),
            new ImagePinDto(
                "qits/workspace",
                "2026.904.160522",
                "qits-workspaces",
                "env.QITS_WORKSPACE_IMAGE_VERSION")),
        configuration.imagePins(),
        "the two unreleased mappings are omitted, and the refinement key of the workspace image is"
            + " one of them — the image is released, that entry is not written");
  }

  @Test
  void theKeyGrammarIsEnforcedOnTheWritePath() {
    assertThrows(
        BadRequestException.class, () -> configuration.upsert(ENV, "app-guard", "volumes[0]", "x", null));
    assertThrows(
        BadRequestException.class, () -> configuration.upsert(ENV, "Bad-App", "env.A", "x", null));
    assertTrue(configuration.entriesOf(ENV, "app-guard").isEmpty());
  }
}
