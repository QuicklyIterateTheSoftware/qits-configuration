package eu.wohlben.qits.configuration.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.junit.QuarkusTest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

/**
 * THE V2 BACKFILL, MIGRATED THROUGH RATHER THAN MIGRATED TO.
 *
 * <p>Every other suite here runs the whole lineage against an EMPTY schema, which means the one
 * statement in V2 that does real work — {@code update … set env = '${legacy_env}'} — touches no rows
 * and is asserted by nothing. This is the repository's first migration test and it exists for that
 * gap: it stops at V1, writes the rows the V1 code wrote, and then migrates the rest of the way, so
 * the claim under test is that an INHERITED store comes out the other side addressable.
 *
 * <p><b>Its own database, and its own Flyway.</b> The suite's Quarkus datasource is already migrated
 * to head by the time any test runs, so a half-migrated schema cannot be borrowed from it — this
 * class asks {@link EmbeddedPg} for a database of its own and drives Flyway by hand. It is a {@code
 * @QuarkusTest} only so that it joins the same JVM and the same embedded postgres as everything else;
 * it injects nothing.
 *
 * <p><b>The inserts name their columns.</b> A positional insert would make every later migration a
 * change to a test that had nothing to do with it.
 *
 * <p>The placeholder is passed explicitly here rather than read from config: this test is about what
 * the SQL does with a value, and wiring it through the config source would put a second mechanism
 * between the assertion and the statement it is about. The shipped value is a literal now — the
 * property that used to fill it is gone — and the name kept here is the suite's own.
 */
@QuarkusTest
class EnvBackfillMigrationTest {

  /** A database of this test's own — the shared one is at head before the suite starts. */
  private static final String DATABASE = "configuration_backfill_test";

  /** What the operator states at the cutover, and what V2 stamps into every inherited row. */
  private static final String LEGACY_ENV = "test";

  private static final String LOCATION = "classpath:db/configuration/migration";

  @Test
  void everyRowWrittenBeforeV2ComesOutOfItNamingTheLegacyEnv() throws Exception {
    String url = EmbeddedPg.url(DATABASE);
    Flyway.configure()
        .dataSource(url, EmbeddedPg.USER, EmbeddedPg.PASSWORD)
        .locations(LOCATION)
        .placeholders(Map.of("legacy_env", LEGACY_ENV))
        .cleanDisabled(false)
        .load()
        .clean();

    // Half way: the schema exactly as the environment-plane service left it, with no env column.
    Flyway.configure()
        .dataSource(url, EmbeddedPg.USER, EmbeddedPg.PASSWORD)
        .locations(LOCATION)
        .placeholders(Map.of("legacy_env", LEGACY_ENV))
        .target("1")
        .load()
        .migrate();

    try (Connection db = DriverManager.getConnection(url, EmbeddedPg.USER, EmbeddedPg.PASSWORD)) {
      assertFalse(hasEnvColumn(db, "configuration_entry"), "V1 has no env column to speak of");

      try (PreparedStatement revision =
          db.prepareStatement(
              "insert into configuration_revision"
                  + " (application, key, value, deleted, updated_by, updated_at)"
                  + " values (?, ?, ?, ?, ?, ?)")) {
        revision.setString(1, "backfilled-app");
        revision.setString(2, "env.QITS_A");
        revision.setString(3, "one");
        revision.setBoolean(4, false);
        revision.setString(5, "alice");
        revision.setObject(6, Instant.now().atOffset(java.time.ZoneOffset.UTC));
        revision.executeUpdate();
      }
      try (PreparedStatement entry =
          db.prepareStatement(
              "insert into configuration_entry"
                  + " (id, application, key, value, class, head_revision, updated_at, updated_by)"
                  + " values (?, ?, ?, ?, ?, ?, ?, ?)")) {
        entry.setObject(1, UUID.randomUUID());
        entry.setString(2, "backfilled-app");
        entry.setString(3, "env.QITS_A");
        entry.setString(4, "one");
        entry.setString(5, "plain");
        entry.setLong(6, 1L);
        entry.setObject(7, Instant.now().atOffset(java.time.ZoneOffset.UTC));
        entry.setString(8, "alice");
        entry.executeUpdate();
      }
    }

    // The rest of the way. This is the statement under test.
    Flyway.configure()
        .dataSource(url, EmbeddedPg.USER, EmbeddedPg.PASSWORD)
        .locations(LOCATION)
        .placeholders(Map.of("legacy_env", LEGACY_ENV))
        .load()
        .migrate();

    try (Connection db = DriverManager.getConnection(url, EmbeddedPg.USER, EmbeddedPg.PASSWORD)) {
      assertTrue(hasEnvColumn(db, "configuration_entry"));
      assertTrue(hasEnvColumn(db, "configuration_revision"));

      assertEquals(LEGACY_ENV, singleEnv(db, "configuration_entry"));
      assertEquals(LEGACY_ENV, singleEnv(db, "configuration_revision"));

      assertFalse(
          isNullable(db, "configuration_entry"),
          "the column is NOT NULL after the backfill: a later insert must state its env");
      assertFalse(isNullable(db, "configuration_revision"));

      assertTrue(
          hasConstraint(db, "uq_configuration_entry_env_application_key"),
          "one current value per (env, application, key)");
      assertFalse(
          hasConstraint(db, "uq_configuration_entry_application_key"),
          "the V1 constraint would forbid the second env this migration exists to allow");
      assertTrue(hasIndex(db, "idx_configuration_revision_env_application_seq"));
      assertFalse(hasIndex(db, "idx_configuration_revision_application_seq"));
    }
  }

  private static boolean hasEnvColumn(Connection db, String table) throws Exception {
    try (ResultSet columns = db.getMetaData().getColumns(null, "public", table, "env")) {
      return columns.next();
    }
  }

  private static boolean isNullable(Connection db, String table) throws Exception {
    try (PreparedStatement query =
        db.prepareStatement(
            "select is_nullable from information_schema.columns"
                + " where table_name = ? and column_name = 'env'")) {
      query.setString(1, table);
      try (ResultSet found = query.executeQuery()) {
        assertTrue(found.next(), "the env column must exist on " + table);
        return "YES".equals(found.getString(1));
      }
    }
  }

  /** The one distinct env in a table, failing when there is not exactly one. */
  private static String singleEnv(Connection db, String table) throws Exception {
    try (Statement query = db.createStatement();
        ResultSet found = query.executeQuery("select distinct env from " + table)) {
      assertTrue(found.next(), table + " must hold the row written before V2");
      String env = found.getString(1);
      assertFalse(found.next(), "the backfill stamps ONE env, not several");
      return env;
    }
  }

  private static boolean hasConstraint(Connection db, String name) throws Exception {
    try (PreparedStatement query =
        db.prepareStatement(
            "select 1 from pg_constraint where conname = ?")) {
      query.setString(1, name);
      try (ResultSet found = query.executeQuery()) {
        return found.next();
      }
    }
  }

  private static boolean hasIndex(Connection db, String name) throws Exception {
    try (PreparedStatement query =
        db.prepareStatement("select 1 from pg_indexes where indexname = ?")) {
      query.setString(1, name);
      try (ResultSet found = query.executeQuery()) {
        return found.next();
      }
    }
  }
}
