package eu.wohlben.qits.configuration.dto;

import java.time.Instant;

/**
 * One current entry, as the API hands it back.
 *
 * <p>{@code revision} is the entry's own head — the revision seq the value came from — so a caller
 * that just wrote can quote what it wrote without a second read.
 *
 * <p><b>{@code entryClass} rather than {@code class}</b>: a record component cannot be named after a
 * Java keyword, and buying the shorter wire name would cost a Jackson annotation on every path this
 * type travels, native-image registration included. The column is still {@code class}.
 */
public record ConfigurationEntryDto(
    String application,
    String key,
    String value,
    String entryClass,
    long revision,
    Instant updatedAt,
    String updatedBy) {}
