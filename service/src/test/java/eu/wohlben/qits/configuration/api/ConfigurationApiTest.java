package eu.wohlben.qits.configuration.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

/**
 * REST round-trips for the configuration boundary.
 *
 * <p>The addresses are the shipped ones — the suite inherits {@code
 * quarkus.rest.path=/configuration/api} from main's application.properties rather than re-declaring
 * it — so a change to the segment fails here rather than in a deployment.
 *
 * <p><b>No test sends an identity header</b>, and that is not a hole: qits-auth-core ships a
 * {@code %test} dev user carrying {@code qits:admin} and {@code qits:system}, so the shipped
 * {@code @RolesAllowed} pair is exercised rather than bypassed. What the suite cannot prove is that
 * the gateway asserts the header; that is the gateway's own contract.
 *
 * <p>Each test names an application of its own. The suite shares one database across classes.
 *
 * <p><b>Two spellings of every route are exercised.</b> The env-addressed ones —
 * {@code /applications/<app>/envs/<env>/…} — are the surface this service keeps; the env-less ones
 * are transitional and answer for {@code qits.configuration.legacy-env}, which this suite sets to
 * {@code test}. The tests that use the short form are therefore also the assertion that the
 * delegation lands where it says it does: what {@code put(...)} writes, {@link
 * #aValueWrittenInOneEnvIsNotVisibleFromAnother} reads back through {@code /envs/test/}.
 */
@QuarkusTest
class ConfigurationApiTest {

  private static final String BASE = "/configuration/api";

  /** What the legacy env-less routes resolve to here — {@code qits.configuration.legacy-env}. */
  private static final String LEGACY_ENV = "test";

  /** A second environment, to prove one env's rows are not the other's. */
  private static final String OTHER_ENV = "staging";

  /** A write through the TRANSITIONAL env-less route, which lands in the legacy env. */
  private void put(String application, String key, String value, int expected) {
    given()
        .contentType(ContentType.JSON)
        .body(new ConfigurationController.SetEntryRequest(value))
        .when()
        .put(BASE + "/applications/" + application + "/entries/" + key)
        .then()
        .statusCode(expected);
  }

  /** A write through the env-addressed route — the spelling that survives the cutover. */
  private void putIn(String env, String application, String key, String value, int expected) {
    given()
        .contentType(ContentType.JSON)
        .body(new ConfigurationController.SetEntryRequest(value))
        .when()
        .put(BASE + "/applications/" + application + "/envs/" + env + "/entries/" + key)
        .then()
        .statusCode(expected);
  }

  @Test
  void aFirstWriteIs201AndARewriteIs200() {
    put("api-create", "env.QITS_REGISTRY", "localhost:8081", 201);
    put("api-create", "env.QITS_REGISTRY", "localhost:8082", 200);

    given()
        .when()
        .get(BASE + "/applications/api-create/entries")
        .then()
        .statusCode(200)
        .body("entries.size()", equalTo(1))
        .body("entries[0].key", equalTo("env.QITS_REGISTRY"))
        .body("entries[0].value", equalTo("localhost:8082"))
        .body("entries[0].entryClass", equalTo("plain"))
        .body("entries[0].revision", greaterThan(0))
        .body("entries[0].updatedBy", notNullValue());
  }

  @Test
  void theResolvedReadCarriesTheFullPropertyNames() {
    put("api-resolve", "env.QITS_A", "one", 201);
    put("api-resolve", "aliases[0]", "api.dev.localhost", 201);

    given()
        .when()
        .get(BASE + "/applications/api-resolve/resolved")
        .then()
        .statusCode(200)
        .body("headRevision", greaterThan(0))
        .body(
            "properties.'qits.platform.deployments.extras.api-resolve.env.QITS_A'", equalTo("one"))
        .body(
            "properties.'qits.platform.deployments.extras.api-resolve.aliases[0]'",
            equalTo("api.dev.localhost"));
  }

  @Test
  void anUnconfiguredApplicationResolvesEmptyRatherThan404() {
    given()
        .when()
        .get(BASE + "/applications/api-unconfigured/resolved")
        .then()
        .statusCode(200)
        .body("headRevision", equalTo(0))
        .body("properties.size()", equalTo(0));
  }

  @Test
  void aDeleteIs204AndTheValueStaysInTheHistory() {
    put("api-delete", "env.A", "one", 201);

    given()
        .when()
        .delete(BASE + "/applications/api-delete/entries/env.A")
        .then()
        .statusCode(204);

    given()
        .when()
        .get(BASE + "/applications/api-delete/entries")
        .then()
        .statusCode(200)
        .body("entries.size()", equalTo(0));

    given()
        .when()
        .get(BASE + "/applications/api-delete/history")
        .then()
        .statusCode(200)
        .body("revisions.size()", equalTo(2))
        .body("revisions[0].deleted", equalTo(true))
        .body("revisions[0].value", nullValue())
        .body("revisions[1].deleted", equalTo(false))
        .body("revisions[1].value", equalTo("one"));
  }

  @Test
  void deletingWhatIsNotThereIs404WithAMessage() {
    given()
        .when()
        .delete(BASE + "/applications/api-missing/entries/env.A")
        .then()
        .statusCode(404)
        .body("message", notNullValue());
  }

  /**
   * The listing is per application, with a row per environment. An application configured in two
   * tiers is ONE entry in {@code applications} carrying TWO rows in its {@code envs} — which is the
   * shape the platform promotion exists to produce, and the one a comparison between tiers can be
   * read off.
   */
  @Test
  void theApplicationListingCarriesOneRowPerEnvironment() {
    put("api-listed", "env.A", "one", 201);
    putIn(OTHER_ENV, "api-listed", "env.A", "one", 201);
    putIn(OTHER_ENV, "api-listed", "env.B", "two", 201);

    given()
        .when()
        .get(BASE + "/applications")
        .then()
        .statusCode(200)
        .body("applications.application", hasItem("api-listed"))
        .body(
            "applications.find { it.application == 'api-listed' }.envs.env",
            equalTo(java.util.List.of(OTHER_ENV, LEGACY_ENV)))
        .body(
            "applications.find { it.application == 'api-listed' }.envs"
                + ".find { it.env == '"
                + LEGACY_ENV
                + "' }.entries",
            equalTo(1))
        .body(
            "applications.find { it.application == 'api-listed' }.envs"
                + ".find { it.env == '"
                + OTHER_ENV
                + "' }.entries",
            equalTo(2))
        .body(
            "applications.find { it.application == 'api-listed' }.envs"
                + ".find { it.env == '"
                + OTHER_ENV
                + "' }.headRevision",
            greaterThan(0));
  }

  /**
   * THE CLAIM THE PLANE FLIP RESTS ON, over the wire: a value written into one env is absent from
   * another, at every route that can be asked. A read that had lost its env predicate would pass a
   * test that only ever looked where it wrote, so each assertion here is a MISS.
   */
  @Test
  void aValueWrittenInOneEnvIsNotVisibleFromAnother() {
    putIn(LEGACY_ENV, "api-envs", "env.A", "from-test", 201);

    given()
        .when()
        .get(BASE + "/applications/api-envs/envs/" + OTHER_ENV + "/entries")
        .then()
        .statusCode(200)
        .body("entries.size()", equalTo(0));

    given()
        .when()
        .get(BASE + "/applications/api-envs/envs/" + OTHER_ENV + "/resolved")
        .then()
        .statusCode(200)
        .body("headRevision", equalTo(0))
        .body("properties.size()", equalTo(0));

    given()
        .when()
        .get(BASE + "/applications/api-envs/envs/" + OTHER_ENV + "/history")
        .then()
        .statusCode(200)
        .body("revisions.size()", equalTo(0));

    given()
        .when()
        .delete(BASE + "/applications/api-envs/envs/" + OTHER_ENV + "/entries/env.A")
        .then()
        .statusCode(404);

    // The same key in the other env is a NEW entry, not an update — 201, not 200.
    putIn(OTHER_ENV, "api-envs", "env.A", "from-staging", 201);

    given()
        .when()
        .get(BASE + "/applications/api-envs/envs/" + LEGACY_ENV + "/entries")
        .then()
        .statusCode(200)
        .body("entries.size()", equalTo(1))
        .body("entries[0].env", equalTo(LEGACY_ENV))
        .body("entries[0].value", equalTo("from-test"));

    given()
        .when()
        .get(BASE + "/applications/api-envs/envs/" + OTHER_ENV + "/entries")
        .then()
        .statusCode(200)
        .body("entries[0].env", equalTo(OTHER_ENV))
        .body("entries[0].value", equalTo("from-staging"));
  }

  /**
   * The transitional route and the env-addressed one are the same door. A write through the short
   * spelling is readable at {@code /envs/<legacy>/}, and the history there records it — which is the
   * whole promise made to a caller that has not learned the segment yet.
   */
  @Test
  void theEnvLessRoutesAnswerForTheLegacyEnv() {
    put("api-legacy", "env.A", "one", 201);

    given()
        .when()
        .get(BASE + "/applications/api-legacy/envs/" + LEGACY_ENV + "/entries")
        .then()
        .statusCode(200)
        .body("entries.size()", equalTo(1))
        .body("entries[0].env", equalTo(LEGACY_ENV))
        .body("entries[0].value", equalTo("one"));

    given()
        .when()
        .get(BASE + "/applications/api-legacy/history")
        .then()
        .statusCode(200)
        .body("revisions.size()", equalTo(1))
        .body("revisions[0].env", equalTo(LEGACY_ENV));
  }

  @Test
  void aRefusedEnvNameIs400AndTheMessageNamesIt() {
    given()
        .when()
        .get(BASE + "/applications/api-envrefuse/envs/Not_An_Env/resolved")
        .then()
        .statusCode(400)
        .body("message", org.hamcrest.Matchers.containsString("Not_An_Env"));
  }

  @Test
  void aRefusedKeyIs400AndTheMessageNamesTheGrammar() {
    given()
        .contentType(ContentType.JSON)
        .body(new ConfigurationController.SetEntryRequest("x"))
        .when()
        .put(BASE + "/applications/api-refuse/entries/volumes[0]")
        .then()
        .statusCode(400)
        .body("message", org.hamcrest.Matchers.containsString("mounts"));
  }

  @Test
  void aRefusedApplicationNameIs400OnAReadToo() {
    given()
        .when()
        .get(BASE + "/applications/Not_A_Label/resolved")
        .then()
        .statusCode(400)
        .body("message", org.hamcrest.Matchers.containsString("Not_A_Label"));
  }

  @Test
  void aMissingValueIs400RatherThanADeletionInDisguise() {
    given()
        .contentType(ContentType.JSON)
        .body("{}")
        .when()
        .put(BASE + "/applications/api-novalue/entries/env.A")
        .then()
        .statusCode(400)
        .body("message", org.hamcrest.Matchers.containsString("DELETE"));
  }
}
