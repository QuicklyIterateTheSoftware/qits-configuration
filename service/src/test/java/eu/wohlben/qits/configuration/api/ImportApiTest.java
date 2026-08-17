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
