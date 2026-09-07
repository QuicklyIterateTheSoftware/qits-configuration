package eu.wohlben.qits.configuration.error;

/**
 * A declaration document this service refuses to read, mapped to HTTP 422.
 *
 * <p><b>Every message names the document and, where there is one, the key</b> — {@code declaration
 * <application>@<version>: key <key>: <what is wrong>}. That prefix is not decoration. The caller is
 * a pipeline step, so the sentence lands in a build log where nobody has the document open beside
 * it, and a refusal that said only "unknown type" would send somebody to read the whole file to find
 * out which line it meant.
 *
 * <p>It is a 422 rather than a 400 for the reason {@link UnprocessableEntityException} gives: the
 * POST was fine, the file the application committed is what needs an edit.
 */
public class DeclarationParseException extends UnprocessableEntityException {

  public DeclarationParseException(String message) {
    super(message);
  }
}
