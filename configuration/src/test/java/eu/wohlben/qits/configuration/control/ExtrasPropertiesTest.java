package eu.wohlben.qits.configuration.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The one spelling this service shares with qits-platform-deployments, read and written.
 *
 * <p>Plain JUnit: parsing a properties file is a decision about a string.
 */
class ExtrasPropertiesTest {

  @Test
  void aPropertyNameIsThePrefixTheApplicationAndTheKey() {
    assertEquals(
        "qits.platform.deployments.extras.qits-gateway.env.QITS_REGISTRY",
        ExtrasProperties.propertyName("qits-gateway", "env.QITS_REGISTRY"));
    assertEquals(
        "qits.platform.deployments.extras.qits-ci.mounts[0]",
        ExtrasProperties.propertyName("qits-ci", "mounts[0]"));
  }

  @Test
  void theApplicationEndsAtTheFirstDotAndTheKeyKeepsTheRest() {
    List<ExtrasProperties.Parsed> parsed =
        ExtrasProperties.parse(
            """
            qits.platform.deployments.extras.qits-gateway.env.QITS_REGISTRY=localhost:8081
            qits.platform.deployments.extras.qits-ci.mounts[0]=/var/run/docker.sock:/var/run/docker.sock
            """);
    assertEquals(2, parsed.size());
    assertEquals("qits-gateway", parsed.get(0).application());
    assertEquals("env.QITS_REGISTRY", parsed.get(0).key());
    assertEquals("localhost:8081", parsed.get(0).value());
    assertEquals("qits-ci", parsed.get(1).application());
    assertEquals("mounts[0]", parsed.get(1).key());
    assertEquals("/var/run/docker.sock:/var/run/docker.sock", parsed.get(1).value());
  }

  @Test
  void commentsBlanksAndUnrelatedKeysAreIgnoredRatherThanRefused() {
    List<ExtrasProperties.Parsed> parsed =
        ExtrasProperties.parse(
            """
            # the deployer's own config volume, imported whole

            qits.platform.deployments.orchestrator=swarm
            ! another comment convention
            qits.platform.deployments.extras.qits-docs.aliases[0]=docs.dev.localhost
            not a property at all
            """);
    assertEquals(1, parsed.size());
    assertEquals("qits-docs", parsed.get(0).application());
    assertEquals("aliases[0]", parsed.get(0).key());
  }

  @Test
  void aValueMayHoldTheSeparatorItselfBecauseOnlyTheFirstOneCounts() {
    List<ExtrasProperties.Parsed> parsed =
        ExtrasProperties.parse(
            "qits.platform.deployments.extras.qits-ci.env.QITS_URL=http://host:8080/a=b");
    assertEquals(1, parsed.size());
    assertEquals("http://host:8080/a=b", parsed.get(0).value());
  }

  @Test
  void aLineCarryingThePrefixButNoKeyIsHandedOnToBeRefusedRatherThanIgnored() {
    // It plainly meant to be an entry, so silently dropping it would lose configuration a person
    // believes they imported. ConfigurationKeys is what turns it into a 400 that names the problem.
    List<ExtrasProperties.Parsed> parsed =
        ExtrasProperties.parse("qits.platform.deployments.extras.qits-ci=something");
    assertEquals(1, parsed.size());
    assertTrue(parsed.get(0).key().isEmpty(), "the key should be empty, not guessed");
  }

  @Test
  void nothingAtAllParsesToNothing() {
    assertEquals(List.of(), ExtrasProperties.parse(null));
    assertEquals(List.of(), ExtrasProperties.parse("   \n\n"));
  }
}
