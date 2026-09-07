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
 * type travels, native-image registration included. The column is still {@code class}. It is the
 * WRITER's own word — {@code plain} for an operator, {@code imported} for the bootstrap's file — and
 * it is what the import path reads to decide whether it may overwrite a row.
 *
 * <p><b>{@code orphaned} is COMPUTED AT READ TIME and stored nowhere.</b> It is true when the
 * application's governing declaration does not account for this key: either it declares no such key
 * at all, or it declares it a {@code serviceAddress}, whose value the platform renders and whose
 * stored row is therefore ignored. Both are the same message to a person — this row is not reaching
 * the container — and neither is an error the service should refuse or clean up. It computes it
 * rather than persisting it because a declaration arriving or being rolled back changes the answer
 * for rows nobody touched, and a stored flag would then be a fact that was true once. An application
 * with no declaration has nothing orphaned: unknown is not the same as unaccounted for.
 */
public record ConfigurationEntryDto(
    String env,
    String application,
    String key,
    String value,
    String entryClass,
    boolean orphaned,
    long revision,
    Instant updatedAt,
    String updatedBy) {}
