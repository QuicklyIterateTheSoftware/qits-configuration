package eu.wohlben.qits.configuration.api;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.wohlben.qits.configuration.testdb.EmbeddedPg;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

/**
 * The surface of the <em>packaged artifact</em> — the fast-jar under {@code ./mvnw verify
 * -DskipITs=false}, the GraalVM binary under {@code -Dnative} — because that is where a whole class
 * of failure is visible and nowhere else.
 *
 * <p>Every other test here is a {@code @QuarkusTest}: it augments and runs in the build JVM, with
 * the full classpath present, reflection unrestricted, and its datasource keys handed to it by a
 * config source. A native image has none of those. What this asserts is exactly what that
 * difference can lose:
 *
 * <ul>
 *   <li>the build-time route prefixes — {@code /configuration/api} and {@code /configuration/q} —
 *       which qits-gateway routes verbatim and no unprefixed form falls back to;
 *   <li>the shipped datasource <b>expression</b>: the launched process is handed {@code
 *       QITS_RESOURCE_DB_*}, the generic contract a deployment supplies, rather than the datasource
 *       keys, so the jar's own {@code ${…}} indirection is what is under test;
 *   <li>Flyway's migration surviving as a classpath resource, proven by reading the written row back
 *       over JDBC rather than through the API that wrote it;
 *   <li>every response type reaching Jackson through {@code Response.entity(...)}, which the
 *       build-time analysis cannot see — that is what {@code ApiWireReflection} is for, and a
 *       missing entry there is a 500 in the binary while the JVM suite stays green.
 * </ul>
 *
 * <p><b>This is also the only place the identity contract is real.</b> A {@code @QuarkusTest} runs
 * under the {@code test} profile, where qits-auth-core ships a dev user; the launched artifact runs
 * as a deployment does, so the roles have to arrive the way qits-gateway sends them — in
 * {@code X-Qits-User} and {@code X-Qits-Roles}. A request with neither is asserted to be refused,
 * which is the claim that this service has no anonymous surface.
 *
 * <p>ITs are skipped by default ({@code skipITs} in the root pom) because they need a {@code
 * package} to have happened. Ask for them explicitly.
 */
@QuarkusIntegrationTest
@TestProfile(PackagedSurfaceIT.PackagedUnderTarget.class)
public class PackagedSurfaceIT {

  /** The database this IT hands the launched process, on a name of its own. */
  private static final String DATABASE = "configuration_packaged_it";

  /**
   * Hands the launched artifact a database the way a deployment does — as the generic resource
   * triple, not as the datasource keys. The configuration jar ships {@code
   * jdbc.url=${QITS_RESOURCE_DB_URL}} and its two siblings, so supplying the variables leaves the
   * <b>shipped</b> expression itself under test.
   *
   * <p>The url travels through a system property rather than a static field: a test profile is
   * instantiated in more than one classloader, so a field written by one copy is not the field the
   * other reads, while the process has exactly one property table.
   */
  public static class PackagedUnderTarget implements QuarkusTestProfile {

    private static final String URL_PROPERTY = "qits.test.packaged-surface-it.db-url";

    @Override
    public Map<String, String> getConfigOverrides() {
      return Map.of(
          "QITS_RESOURCE_DB_URL", databaseUrl(),
          "QITS_RESOURCE_DB_USERNAME", EmbeddedPg.USER,
          "QITS_RESOURCE_DB_PASSWORD", EmbeddedPg.PASSWORD);
    }

    private static synchronized String databaseUrl() {
      String recorded = System.getProperty(URL_PROPERTY);
      if (recorded != null) {
        return recorded;
      }
      // localhost resolves for the launched process too — it is a child of this JVM on this host.
      String url = EmbeddedPg.url(DATABASE);
      System.setProperty(URL_PROPERTY, url);
      return url;
    }
  }

  /** What qits-gateway asserts for an authenticated operator. */
  private static RequestSpecification asAdmin() {
    return given().header("X-Qits-User", "packaged-it").header("X-Qits-Roles", "qits:admin");
  }

  @Test
  public void anEntryRoundTripsThroughFlywayAndPanacheOnTheShippedDatasource() {
    asAdmin()
        .contentType(ContentType.JSON)
        .body(new ConfigurationController.SetEntryRequest("localhost:8081"))
        .when()
        .put("/configuration/api/applications/packaged/entries/env.QITS_REGISTRY")
        .then()
        .statusCode(201)
        .body("entry.value", Matchers.equalTo("localhost:8081"));

    asAdmin()
        .when()
        .get("/configuration/api/applications/packaged/resolved")
        .then()
        .statusCode(200)
        .contentType(ContentType.JSON)
        .body(
            "properties.'qits.platform.deployments.extras.packaged.env.QITS_REGISTRY'",
            Matchers.equalTo("localhost:8081"))
        .body("headRevision", Matchers.greaterThan(0));

    // The round trip above would look identical against any database at all, so read the row back
    // out of the postgres this JVM handed the process through ${QITS_RESOURCE_DB_URL}. That is the
    // whole claim: the shipped expression resolved, and Flyway's migration survived as a classpath
    // resource — exactly the shape a native image drops.
    assertTrue(
        rowExists("packaged", "env.QITS_REGISTRY"),
        "the packaged process must have written into the resource database");
  }

  @Test
  public void theImportRouteTakesAPropertiesFileOnTheArtifact() {
    asAdmin()
        .contentType(ContentType.TEXT)
        .body(
            """
            # exported from the deployer's config volume
            qits.platform.deployments.extras.packaged-import.aliases[0]=packaged.dev.localhost
            """)
        .when()
        .post("/configuration/api/import")
        .then()
        .statusCode(200)
        .contentType(ContentType.JSON)
        .body("imported", Matchers.equalTo(1))
        .body("ignored", Matchers.equalTo(1));
  }

  @Test
  public void thereIsNoAnonymousSurface() {
    given().when().get("/configuration/api/applications").then().statusCode(401);
  }

  @Test
  public void theRoutesAreWhereTheGatewayRoutesThemAndAMistypedOneIsNever200() {
    asAdmin().when().get("/configuration/api/applications").then().statusCode(200);

    // qits-gateway routes verbatim by prefix, so there is no unprefixed form to fall back to.
    asAdmin().when().get("/api/applications").then().statusCode(404);

    // A mistyped machine path answers Vert.x' own stock page, which is text/html and correct — so
    // what is pinned is the status and the absence of anything a client would parse as data.
    String body =
        asAdmin().when().get("/configuration/api/nope").then().statusCode(404).extract().asString();
    assertFalse(body.contains("headRevision"), "a mistyped path must not answer with data: " + body);
  }

  @Test
  public void theReadinessEndpointIsWhereTheDeploymentLooksForIt() {
    given()
        .when()
        .get("/configuration/q/health/ready")
        .then()
        .statusCode(200)
        .body("status", Matchers.equalTo("UP"));
  }

  @Test
  public void theApiDocumentAndItsUiAreServedUnderTheSegment() {
    // Both live under quarkus.http.non-application-root-path, which sits OUTSIDE quarkus.rest.path
    // and carries /configuration on its own; at / they would be unreachable through qits-gateway.
    given().when().get("/configuration/q/openapi").then().statusCode(200);
    given().when().get("/configuration/q/swagger-ui/").then().statusCode(200);
  }

  private static boolean rowExists(String application, String key) {
    String url = EmbeddedPg.url(DATABASE);
    try (Connection connection =
            DriverManager.getConnection(url, EmbeddedPg.USER, EmbeddedPg.PASSWORD);
        PreparedStatement query =
            connection.prepareStatement(
                "select 1 from configuration_entry where application = ? and key = ?")) {
      query.setString(1, application);
      query.setString(2, key);
      try (ResultSet found = query.executeQuery()) {
        return found.next();
      }
    } catch (Exception e) {
      throw new IllegalStateException("could not read the resource database back", e);
    }
  }
}
