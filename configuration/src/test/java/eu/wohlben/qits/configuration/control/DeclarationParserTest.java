package eu.wohlben.qits.configuration.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.wohlben.qits.configuration.control.DeclarationParser.DeclaredKey;
import eu.wohlben.qits.configuration.error.DeclarationParseException;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The declaration grammar, and the sentences a refusal comes back with.
 *
 * <p>Plain JUnit, like {@link ConfigurationKeysTest}: parsing a document is a decision about a
 * string and needs no application to run in.
 *
 * <p><b>The messages are asserted, not only the refusals.</b> The caller is a pipeline step, so the
 * sentence lands in a build log where nobody has the document open beside it — "it said 422" is not
 * the property worth pinning, "it said which key and what about it" is.
 *
 * <p><b>Every refusal is asserted to name the document as well.</b> A build log carries the output
 * of many steps, and a refusal that did not say which application at which version it was reading
 * would be a line somebody has to correlate by hand.
 */
class DeclarationParserTest {

  private static final String APP = "qits-declared";
  private static final String VERSION = "2026.907.1";

  private static DeclarationParser.Declaration parse(String raw) {
    return DeclarationParser.parse(APP, VERSION, raw);
  }

  private static String refusal(String raw) {
    DeclarationParseException failure =
        assertThrows(DeclarationParseException.class, () -> parse(raw));
    assertEquals(422, failure.statusCode());
    assertTrue(
        failure.getMessage().startsWith("declaration " + APP + "@" + VERSION + ":"),
        "every refusal names the document it was reading: " + failure.getMessage());
    return failure.getMessage();
  }

  private static DeclaredKey only(String raw) {
    List<DeclaredKey> keys = parse(raw).keys();
    assertEquals(1, keys.size());
    return keys.get(0);
  }

  // ---------------------------------------------------------------- the top level

  @Test
  void anEmptyKeysMapIsAWholeDeclaration() {
    assertTrue(parse("keys: {}\n").keys().isEmpty());
    assertTrue(
        parse("keys:\n").keys().isEmpty(),
        "`keys:` with nothing under it is an application that declares no keys, which is a complete"
            + " statement and not a broken document");
  }

  @Test
  void theTopLevelIsClosedAndTheRefusalNamesTheStrayKey() {
    String message = refusal("keys: {}\ndeployment_target: platform\n");
    assertTrue(
        message.contains("deployment_target"),
        "the refusal names the key it did not expect: " + message);
    assertTrue(
        message.contains("deployer"),
        "and says where that fact actually comes from: " + message);
  }

  @Test
  void aDocumentWithNoKeysMappingIsRefused() {
    assertTrue(refusal("# nothing but a comment\n").contains("mapping"));
    assertTrue(refusal("- one\n- two\n").contains("mapping"));
    assertTrue(refusal("   ").contains("empty"));
  }

  @Test
  void aDocumentThatIsNotYamlIsRefusedAsSuch() {
    assertTrue(refusal("keys:\n  a: [oops\n").contains("not readable as YAML"));
  }

  @Test
  void aKeyDeclaredTwiceIsAnAmbiguityRatherThanACorrection() {
    String message =
        refusal(
            """
            keys:
              env.QITS_A:
                type: string
              env.QITS_A:
                type: number
            """);
    assertTrue(message.contains("not readable as YAML"), message);
    assertTrue(message.contains("env.QITS_A"), "and names the key that was doubled: " + message);
  }

  // ---------------------------------------------------------------- key names and types

  @Test
  void aDeclaredKeyNameGoesThroughTheSameGrammarAStoredOneDoes() {
    assertEquals("env.QITS_A", only("keys:\n  env.QITS_A:\n    type: string\n").key());
    assertEquals("mounts[0]", only("keys:\n  mounts[0]:\n    type: string\n").key());

    String message = refusal("keys:\n  volumes[0]:\n    type: string\n");
    assertTrue(message.contains("key volumes[0]"), "the refusal names the key: " + message);
    assertTrue(
        message.contains("`env.<VAR>`"),
        "and it is the STORED key grammar's own sentence, not a second one: " + message);
  }

  @Test
  void aKeyWithNoTypeIsRefusedAndTheVocabularyIsListed() {
    String message = refusal("keys:\n  env.QITS_A:\n    default: one\n");
    assertTrue(message.contains("key env.QITS_A: no type"), message);
    assertTrue(
        message.contains("string, boolean, number, serviceAddress, packageVersion"), message);
  }

  @Test
  void anUnknownTypeIsRefusedByName() {
    String message = refusal("keys:\n  env.QITS_A:\n    type: integer\n");
    assertEquals(
        "declaration "
            + APP
            + "@"
            + VERSION
            + ": key env.QITS_A: unknown type 'integer'. A type is one of string, boolean, number,"
            + " serviceAddress, packageVersion.",
        message,
        "the whole sentence is the contract, not just the fact that it refused");
  }

  @Test
  void aKeyThatIsNotAMappingIsRefused() {
    assertTrue(refusal("keys:\n  env.QITS_A: string\n").contains("must be a mapping"));
  }

  // ---------------------------------------------------------------- string, boolean, number

  @Test
  void aStringKeyTakesAnOptionalDefault() {
    DeclaredKey withDefault =
        only("keys:\n  env.QITS_A:\n    type: string\n    default: one\n    description: why\n");
    assertEquals(DeclarationParser.TYPE_STRING, withDefault.type());
    assertEquals("one", withDefault.defaultValue());

    DeclaredKey without = only("keys:\n  env.QITS_A:\n    type: string\n");
    assertNull(
        without.defaultValue(),
        "no default is a declaration too: the key exists and carries nothing until somebody sets it");
  }

  @Test
  void anEmptyStringIsADefaultAndAnAbsentOneIsNot() {
    assertEquals("", only("keys:\n  env.QITS_A:\n    type: string\n    default: \"\"\n").defaultValue());
    assertTrue(
        refusal("keys:\n  env.QITS_A:\n    type: string\n    default:\n").contains("default is empty"),
        "`default:` with nothing after it is a line somebody did not finish writing");
  }

  @Test
  void aBooleanDefaultIsOneOfTwoWordsAndIsStoredAsText() {
    assertEquals("true", only("keys:\n  env.QITS_A:\n    type: boolean\n    default: true\n").defaultValue());
    assertEquals(
        "false", only("keys:\n  env.QITS_A:\n    type: boolean\n    default: \"false\"\n").defaultValue());

    String message = refusal("keys:\n  env.QITS_A:\n    type: boolean\n    default: maybe\n");
    assertTrue(message.contains("default must be true or false, not 'maybe'"), message);
  }

  @Test
  void aNumberDefaultMustParseAsDecimal() {
    assertEquals("8080", only("keys:\n  env.QITS_A:\n    type: number\n    default: 8080\n").defaultValue());
    assertEquals("1.5", only("keys:\n  env.QITS_A:\n    type: number\n    default: 1.5\n").defaultValue());
    assertEquals("-3", only("keys:\n  env.QITS_A:\n    type: number\n    default: -3\n").defaultValue());

    assertTrue(
        refusal("keys:\n  env.QITS_A:\n    type: number\n    default: lots\n")
            .contains("default must be a decimal number, not 'lots'"));
  }

  @Test
  void aDefaultMustBeAScalar() {
    assertTrue(
        refusal("keys:\n  env.QITS_A:\n    type: string\n    default:\n      a: b\n")
            .contains("default must be a scalar, not a mapping"));
    assertTrue(
        refusal("keys:\n  env.QITS_A:\n    type: string\n    default:\n      - a\n")
            .contains("default must be a scalar, not a list"));
  }

  @Test
  void aValuedKeysAttributesAreClosed() {
    String message = refusal("keys:\n  env.QITS_A:\n    type: string\n    required: true\n");
    assertTrue(message.contains("unknown attribute 'required' on a string key"), message);
    assertTrue(message.contains("type, default and description"), message);
  }

  // ---------------------------------------------------------------- serviceAddress

  @Test
  void aServiceAddressCarriesAnApplicationAndAPortAndNothingElse() {
    DeclaredKey key =
        only(
            """
            keys:
              env.QITS_EVENTS_URL:
                type: serviceAddress
                service: qits-events
                port: 8080
                description: the platform bus
            """);
    assertEquals(DeclarationParser.TYPE_SERVICE_ADDRESS, key.type());
    assertEquals("qits-events", key.serviceRef());
    assertEquals(8080, key.servicePort());
    assertNull(key.defaultValue(), "a rendered address has no fallback and must not be given one");
  }

  @Test
  void aServiceAddressTakesNoPathAndNoDefault() {
    String path =
        refusal(
            "keys:\n  env.A:\n    type: serviceAddress\n    service: qits-events\n    port: 8080\n"
                + "    path: /events\n");
    assertTrue(path.contains("unknown attribute 'path' on a serviceAddress key"), path);
    assertTrue(path.contains("type, service, port and description"), path);

    assertTrue(
        refusal(
                "keys:\n  env.A:\n    type: serviceAddress\n    service: qits-events\n    port:"
                    + " 8080\n    default: http://x\n")
            .contains("unknown attribute 'default' on a serviceAddress key"));
  }

  @Test
  void theServiceIsAnApplicationNameAndIsRefusedAsOne() {
    assertTrue(
        refusal("keys:\n  env.A:\n    type: serviceAddress\n    port: 8080\n").contains("no service"),
        "a serviceAddress with nothing to address is not a declaration");

    String message =
        refusal("keys:\n  env.A:\n    type: serviceAddress\n    service: Qits_Events\n    port: 8080\n");
    assertTrue(message.contains("service"), message);
    assertTrue(
        message.contains("Not a valid application name: Qits_Events"),
        "the application grammar's own sentence, reused rather than restated: " + message);
  }

  @Test
  void thePortIsRequiredAndBounded() {
    assertTrue(
        refusal("keys:\n  env.A:\n    type: serviceAddress\n    service: qits-events\n")
            .contains("no port"));
    for (String bad : new String[] {"0", "65536", "eighty", "-1"}) {
      String message =
          refusal(
              "keys:\n  env.A:\n    type: serviceAddress\n    service: qits-events\n    port: "
                  + bad
                  + "\n");
      assertTrue(
          message.contains("port must be a whole number between 1 and 65535"),
          "port " + bad + ": " + message);
    }
    assertEquals(
        65535,
        only("keys:\n  env.A:\n    type: serviceAddress\n    service: qits-events\n    port: 65535\n")
            .servicePort(),
        "the bound is inclusive");
  }

  // ---------------------------------------------------------------- packageVersion

  @Test
  void aPackageVersionNamesANestedPackageAndNoDefault() {
    DeclaredKey key =
        only(
            """
            keys:
              env.QITS_WORKSPACE_IMAGE_VERSION:
                type: packageVersion
                package:
                  type: docker
                  name: qits/workspace
            """);
    assertEquals(DeclarationParser.TYPE_PACKAGE_VERSION, key.type());
    assertEquals("docker", key.packageType());
    assertEquals("qits/workspace", key.packageName());
    assertNull(
        key.defaultValue(),
        "a hard-coded fallback version is a container started on a tag nobody shipped");

    assertTrue(
        refusal(
                "keys:\n  env.A:\n    type: packageVersion\n    package:\n      type: docker\n     "
                    + " name: x\n    default: 1.0\n")
            .contains("unknown attribute 'default' on a packageVersion key"));
  }

  @Test
  void aPackageIsRequiredAndClosedAndBothOfItsFieldsAreNeeded() {
    assertTrue(refusal("keys:\n  env.A:\n    type: packageVersion\n").contains("no package"));

    assertTrue(
        refusal("keys:\n  env.A:\n    type: packageVersion\n    package:\n      name: x\n")
            .contains("package.type is required"));
    assertTrue(
        refusal("keys:\n  env.A:\n    type: packageVersion\n    package:\n      type: docker\n")
            .contains("package.name is required"));

    String message =
        refusal(
            "keys:\n  env.A:\n    type: packageVersion\n    package:\n      type: docker\n      name:"
                + " x\n      registry: here\n");
    assertTrue(message.contains("unknown attribute 'registry' on a package"), message);
    assertTrue(message.contains("type and name"), message);
  }

  // ---------------------------------------------------------------- the hash

  @Test
  void theHashIsOverTheDocumentAndNotOverWhatWasParsedOutOfIt() {
    String document = "keys:\n  env.QITS_A:\n    type: string\n    default: one\n";
    assertEquals(parse(document).contentHash(), parse(document).contentHash());
    assertEquals(64, parse(document).contentHash().length(), "sha-256, hex");

    assertNotEquals(
        parse(document).contentHash(),
        parse("# a comment nobody parses\n" + document).contentHash(),
        "two builds publishing one version from different sources is exactly what the conflict"
            + " exists to surface, and a hash over the parsed keys would hide it");
  }

  @Test
  void theDocumentIsKeptVerbatimBesideTheParsedKeys() {
    String document =
        "keys:\n  env.QITS_A:\n    type: string\n    description: kept for the reader\n";
    assertEquals(document, parse(document).raw());
    assertEquals(APP, parse(document).application());
    assertEquals(VERSION, parse(document).version());
  }

  @Test
  void keysComeBackInTheOrderTheDocumentWroteThem() {
    List<DeclaredKey> keys =
        parse(
                """
                keys:
                  env.Z:
                    type: string
                  env.A:
                    type: string
                  mounts[0]:
                    type: string
                """)
            .keys();
    assertEquals(
        List.of("env.Z", "env.A", "mounts[0]"),
        keys.stream().map(DeclaredKey::key).toList(),
        "document order, so a person diffing the parsed view against the file reads them side by"
            + " side");
  }
}
