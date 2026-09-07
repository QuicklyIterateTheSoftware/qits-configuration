package eu.wohlben.qits.configuration.dto;

import java.time.Instant;

/**
 * One current entry, as the API hands it back.
 *
 * <p>{@code revision} is the entry's own head — the revision seq the value came from — so a caller
 * that just wrote can quote what it wrote without a second read.
 *
 * <p><b>{@code env} is on the wire even where the caller named it in the path</b>, because an entry
 * read out of one response and quoted into another is only identifiable with it. A shape that
 * dropped it would be a shape whose meaning depended on which route returned it.
 *
 * <p><b>{@code entryClass} rather than {@code class}</b>: a record component cannot be named after a
 * Java keyword, and buying the shorter wire name would cost a Jackson annotation on every path this
 * type travels, native-image registration included. The column is still {@code class}.
 */
public record ConfigurationEntryDto(
    String env,
    String application,
    String key,
    String value,
    String entryClass,
    long revision,
    Instant updatedAt,
    String updatedBy) {}
