package eu.wohlben.qits.configuration.testdb;

import java.util.Map;
import java.util.Set;
import org.eclipse.microprofile.config.spi.ConfigSource;

/**
 * Hands the running {@link EmbeddedPg} to every {@code @QuarkusTest} in this module, as the three
 * keys a deployment would supply — {@code jdbc.url}, {@code username}, {@code password} — for
 * <b>both</b> datasources this deployable boots.
 *
 * <p>Two of them, because joining the qits-eventstream jar makes this deployable a subscriber: the
 * outbox arrives with its own datasource, its own persistence unit and its own Flyway lineage, and
 * being dark in {@code %test} does not stop any of that. {@code qits.eventstream.enabled=false} stops
 * publishing, sweeping and dialling; Quarkus still opens the connection and migrates at boot. So the
 * outbox gets a database here or the suite does not start.
 *
 * <p>It is a config source rather than six lines in {@code
 * src/test/resources/application.properties} because the port is chosen at run time — the instance
 * takes a free one, so nothing can be written down ahead of the JVM that starts it.
 *
 * <p>The ordinal sits above application.properties (250) so this wins over both jars' shipped
 * defaults (100) and anything the test properties file might carry, and it is registered through
 * {@code META-INF/services}, which is how a config source joins a Quarkus application without being
 * a bean.
 *
 * <p>It supplies the DATASOURCE keys rather than the {@code QITS_RESOURCE_*} triples the shipped
 * defaults expand: the packaged-artifact IT in {@code service} takes those, because there the point
 * is to exercise the shipped expression itself. Here the point is only to have a database.
 */
public class EmbeddedPgConfigSource implements ConfigSource {

  /** This module's database on the shared instance — the domain module names its own. */
  private static final String DATABASE = "configuration_svc";

  /**
   * The outbox's store. Named for this module too, and deliberately NOT {@code eventstream_test} —
   * that is the qits-eventstream library's own suite's database, and a consumer must not mean it.
   */
  private static final String EVENTSTREAM_DATABASE = "eventstream_svc";

  private static final String PREFIX = "quarkus.datasource.configuration.";

  private static final String EVENTSTREAM_PREFIX = "quarkus.datasource.eventstream.";

  private final Map<String, String> values =
      Map.of(
          PREFIX + "jdbc.url", EmbeddedPg.url(DATABASE),
          PREFIX + "username", EmbeddedPg.USER,
          PREFIX + "password", EmbeddedPg.PASSWORD,
          EVENTSTREAM_PREFIX + "jdbc.url", EmbeddedPg.url(EVENTSTREAM_DATABASE),
          EVENTSTREAM_PREFIX + "username", EmbeddedPg.USER,
          EVENTSTREAM_PREFIX + "password", EmbeddedPg.PASSWORD);

  @Override
  public int getOrdinal() {
    return 500;
  }

  @Override
  public Set<String> getPropertyNames() {
    return values.keySet();
  }

  @Override
  public String getValue(String propertyName) {
    return values.get(propertyName);
  }

  @Override
  public String getName() {
    return "embedded-pg";
  }
}
