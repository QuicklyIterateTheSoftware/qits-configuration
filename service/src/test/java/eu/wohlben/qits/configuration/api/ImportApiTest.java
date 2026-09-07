package eu.wohlben.qits.configuration.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

/**
 * The bulk import, over the wire, with a body shaped like the file it replaces: a real extras
 * properties file, comments and unrelated keys included.
 */
@QuarkusTest
class ImportApiTest {

  private static final String BASE = "/configuration/api";

  private static final String FILE =
      """
      # qits-platform-deployments config volume, exported
      qits.platform.deployments.orchestrator=swarm

      qits.platform.deployments.extras.imp-one.env.QITS_REGISTRY=localhost:8081
      qits.platform.deployments.extras.imp-one.mounts[0]=/data:/data
      qits.platform.deployments.extras.imp-two.aliases[0]=two.dev.localhost
      """;

  @Test
  void anImportWritesTheEntriesAndIsFreeToRepeat() {
    given()
        .contentType(ContentType.TEXT)
        .body(FILE)
        .when()
        .post(BASE + "/import")
        .then()
        .statusCode(200)
        .body("imported", equalTo(3))
        .body("unchanged", equalTo(0))
        .body("ignored", equalTo(3));

    given()
        .when()
        .get(BASE + "/applications/imp-one/resolved")
        .then()
        .statusCode(200)
        .body(
            "properties.'qits.platform.deployments.extras.imp-one.env.QITS_REGISTRY'",
            equalTo("localhost:8081"))
        .body(
            "properties.'qits.platform.deployments.extras.imp-one.mounts[0]'",
            equalTo("/data:/data"));

    // The second run is the one that matters: a bootstrap re-imports on every boot, and a history
    // that grew a revision per run would be a history nobody could read.
    given()
        .contentType(ContentType.TEXT)
        .body(FILE)
        .when()
        .post(BASE + "/import")
        .then()
        .statusCode(200)
        .body("imported", equalTo(0))
        .body("unchanged", equalTo(3));

    given()
        .when()
        .get(BASE + "/applications/imp-one/history")
        .then()
        .statusCode(200)
        .body("revisions.size()", equalTo(2));
  }

  @Test
  void aLineCarryingThePrefixAndNothingUsableIs400() {
    given()
        .contentType(ContentType.TEXT)
        .body("qits.platform.deployments.extras.imp-bad.volumes[0]=nope\n")
        .when()
        .post(BASE + "/import")
        .then()
        .statusCode(400)
        .body("message", notNullValue());
  }

  /**
   * The import names the env it asserts. The same file imported into another environment is a whole
   * new set of entries — 3 imported again, not 3 unchanged — because an entry is
   * {@code (env, application, key)} and the second import shares only two thirds of that with the
   * first.
   *
   * <p>The read afterwards is the sharper half: the env-addressed resolved route in the imported env
   * carries the values, and the one in the legacy env does not know the application at all.
   */
  @Test
  void anImportNamesTheEnvItAsserts() {
    String file =
        """
        qits.platform.deployments.extras.imp-env.env.QITS_A=one
        qits.platform.deployments.extras.imp-env.env.QITS_B=two
        """;

    given()
        .contentType(ContentType.TEXT)
        .body(file)
        .when()
        .post(BASE + "/import?env=staging")
        .then()
        .statusCode(200)
        .body("imported", equalTo(2))
        .body("unchanged", equalTo(0));

    given()
        .contentType(ContentType.TEXT)
        .body(file)
        .when()
        .post(BASE + "/import?env=staging")
        .then()
        .statusCode(200)
        // The same file into the same env is still idempotent: nothing changed, so nothing appended.
        .body("imported", equalTo(0))
        .body("unchanged", equalTo(2));

    given()
        .when()
        .get(BASE + "/applications/imp-env/envs/staging/resolved")
        .then()
        .statusCode(200)
        .body(
            "properties.'qits.platform.deployments.extras.imp-env.env.QITS_A'", equalTo("one"));

    given()
        .when()
        .get(BASE + "/applications/imp-env/envs/test/resolved")
        .then()
        .statusCode(200)
        .body("headRevision", equalTo(0))
        .body("properties.size()", equalTo(0));
  }

  /**
   * An import with no {@code env} goes to the legacy env — the transitional default that carries a
   * bootstrap which has not learned the parameter across the plane flip. It is asserted through the
   * env-addressed read, so the test says WHICH env the default resolved to rather than merely that
   * something was written.
   */
  @Test
  void anImportWithoutAnEnvGoesToTheLegacyEnv() {
    given()
        .contentType(ContentType.TEXT)
        .body("qits.platform.deployments.extras.imp-default.env.QITS_A=one\n")
        .when()
        .post(BASE + "/import")
        .then()
        .statusCode(200)
        .body("imported", equalTo(1));

    given()
        .when()
        .get(BASE + "/applications/imp-default/envs/test/entries")
        .then()
        .statusCode(200)
        .body("entries.size()", equalTo(1))
        .body("entries[0].env", equalTo("test"));
  }

  @Test
  void aRefusedEnvOnTheImportIs400AndNothingIsWritten() {
    given()
        .contentType(ContentType.TEXT)
        .body("qits.platform.deployments.extras.imp-badenv.env.QITS_A=one\n")
        .when()
        .post(BASE + "/import?env=Not_An_Env")
        .then()
        .statusCode(400)
        .body("message", notNullValue());

    given()
        .when()
        .get(BASE + "/applications/imp-badenv/envs/test/entries")
        .then()
        .statusCode(200)
        .body("entries.size()", equalTo(0));
  }

  @Test
  void anEmptyBodyImportsNothingAndSaysSo() {
    given()
        .contentType(ContentType.TEXT)
        .body("")
        .when()
        .post(BASE + "/import")
        .then()
        .statusCode(200)
        .body("imported", equalTo(0))
        .body("unchanged", equalTo(0))
        .body("ignored", equalTo(0));
  }
}
