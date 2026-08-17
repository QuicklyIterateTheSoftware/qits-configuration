package eu.wohlben.qits.configuration.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.wohlben.qits.configuration.error.BadRequestException;
import org.junit.jupiter.api.Test;

/**
 * The grammar, and the sentences a refusal comes back with.
 *
 * <p>Plain JUnit: validation is a decision about a string and needs no application to run in.
 *
 * <p>The messages are asserted, not only the refusals. A caller here is usually a person typing a
 * key by hand or a bootstrap script whose only output is this sentence, so "it said 400" is not the
 * property worth pinning — "it said which part was wrong" is.
 */
class ConfigurationKeysTest {

  @Test
  void applicationNamesAreDnsLabelShaped() {
    assertEquals("qits-gateway", ConfigurationKeys.requireApplication("qits-gateway"));
    assertEquals("a", ConfigurationKeys.requireApplication("a"));
    assertEquals("qits-ci", ConfigurationKeys.requireApplication("  qits-ci  "));
  }

  @Test
  void anApplicationNameThatIsNotADnsLabelIsRefusedByName() {
    for (String refused : new String[] {"Qits-Gateway", "1qits", "qits_gateway", "qits-", "qits.ci"}) {
      BadRequestException failure =
          assertThrows(BadRequestException.class, () -> ConfigurationKeys.requireApplication(refused));
      assertTrue(
          failure.getMessage().contains(refused),
          "the refusal should name the value it refused: " + failure.getMessage());
      assertEquals(400, failure.statusCode());
    }
  }

  @Test
  void aMissingApplicationNameIsRefused() {
    assertThrows(BadRequestException.class, () -> ConfigurationKeys.requireApplication(null));
    assertThrows(BadRequestException.class, () -> ConfigurationKeys.requireApplication("   "));
  }

  @Test
  void environmentKeysTakeShellVariableNames() {
    assertEquals("env.QITS_REGISTRY", ConfigurationKeys.requireKey("env.QITS_REGISTRY"));
    assertEquals("env._private", ConfigurationKeys.requireKey("env._private"));
    assertEquals("env.a1", ConfigurationKeys.requireKey("env.a1"));
  }

  @Test
  void theFourIndexedFamiliesTakeOneToFourDigits() {
    assertEquals("mounts[0]", ConfigurationKeys.requireKey("mounts[0]"));
    assertEquals("publishes[12]", ConfigurationKeys.requireKey("publishes[12]"));
    assertEquals("groups[999]", ConfigurationKeys.requireKey("groups[999]"));
    assertEquals("aliases[1234]", ConfigurationKeys.requireKey("aliases[1234]"));
  }

  @Test
  void aBrokenEnvironmentKeyIsRefusedAsAnEnvironmentKey() {
    BadRequestException failure =
        assertThrows(BadRequestException.class, () -> ConfigurationKeys.requireKey("env.1BAD"));
    assertTrue(
        failure.getMessage().contains("environment variable"),
        "an env.* key should be refused as one: " + failure.getMessage());
  }

  @Test
  void aBrokenIndexIsRefusedAsAnIndexAndNamesItsFamily() {
    BadRequestException failure =
        assertThrows(BadRequestException.class, () -> ConfigurationKeys.requireKey("mounts[abc]"));
    assertTrue(
        failure.getMessage().contains("mounts"),
        "the refusal should name the family: " + failure.getMessage());
    assertTrue(
        failure.getMessage().contains("index"),
        "the refusal should say the index is the problem: " + failure.getMessage());
    assertThrows(BadRequestException.class, () -> ConfigurationKeys.requireKey("mounts[12345]"));
    assertThrows(BadRequestException.class, () -> ConfigurationKeys.requireKey("mounts[]"));
  }

  @Test
  void anUnknownFamilyIsRefusedWithTheWholeGrammar() {
    BadRequestException failure =
        assertThrows(BadRequestException.class, () -> ConfigurationKeys.requireKey("volumes[0]"));
    String message = failure.getMessage();
    for (String family : new String[] {"env.", "mounts", "publishes", "groups", "aliases"}) {
      assertTrue(message.contains(family), "the grammar should be listed: " + message);
    }
  }

  @Test
  void aKeyWithNoFamilyAtAllIsRefused() {
    assertThrows(BadRequestException.class, () -> ConfigurationKeys.requireKey("QITS_REGISTRY"));
    assertThrows(BadRequestException.class, () -> ConfigurationKeys.requireKey("env"));
    assertThrows(BadRequestException.class, () -> ConfigurationKeys.requireKey("env."));
    assertThrows(BadRequestException.class, () -> ConfigurationKeys.requireKey(null));
  }

  @Test
  void theEmptyStringIsAValueAndNullIsNot() {
    assertEquals("", ConfigurationKeys.requireValue(""));
    BadRequestException failure =
        assertThrows(BadRequestException.class, () -> ConfigurationKeys.requireValue(null));
    assertTrue(
        failure.getMessage().contains("DELETE"),
        "a missing value should point at the operation that removes an entry: "
            + failure.getMessage());
  }
}
