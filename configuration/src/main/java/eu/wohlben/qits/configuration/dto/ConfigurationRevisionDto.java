package eu.wohlben.qits.configuration.dto;

import java.time.Instant;

/**
 * One recorded write.
 *
 * <p>{@code value} is null exactly when {@code deleted} is true. The pair is deliberate: an entry
 * may hold the empty string, so a null value alone could not tell a deletion from a blanking.
 */
public record ConfigurationRevisionDto(
    long seq,
    String application,
    String key,
    String value,
    boolean deleted,
    Instant updatedAt,
    String updatedBy) {}
