package eu.wohlben.qits.configuration.dto;

/**
 * One declared key, as the API hands it back: flat, with the fields its own type does not use left
 * null.
 *
 * <p><b>The nulls are the shape and not an omission.</b> A caller reading this list is asking "what
 * does this version declare", and a per-type envelope would make the answer a tagged union every
 * consumer has to switch on before it can display a row. {@code type} is the tag; the columns beside
 * it say which fields are populated.
 *
 * <p><b>No rendered address here.</b> A serviceAddress row carries {@code service} and {@code port},
 * never the host they resolve to — that depends on which environment is asking and on which plane
 * the named application deploys onto, so it exists only in a resolved read for one environment. This
 * listing is the declaration, which is one document for every environment at once.
 */
public record DeclaredKeyDto(
    String key,
    String type,
    String defaultValue,
    String service,
    Integer port,
    String packageType,
    String packageName) {}
