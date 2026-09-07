package eu.wohlben.qits.configuration.error;

/**
 * Configuration error mapped to HTTP 422 by the web layer: the request was addressed correctly and
 * was syntactically fine, and this service still cannot act on what it says.
 *
 * <p><b>422 rather than 400, and the difference is who has to fix it.</b> A 400 here means the
 * caller mistyped something in the request it just made — a key outside the grammar, an env that is
 * not a name — and the fix is one edit away in the same terminal. A 422 means the request was
 * well-formed and the DOCUMENT or the STATE behind it is what refuses: a declaration whose YAML is
 * legal but whose types are not, or a service address pointing at an application that has never said
 * which plane it deploys onto. Those are fixed somewhere else, by somebody else, and answering them
 * with the same status as a typo would hide that.
 */
public class UnprocessableEntityException extends ConfigurationException {

  public UnprocessableEntityException(String message) {
    super(422, message);
  }
}
