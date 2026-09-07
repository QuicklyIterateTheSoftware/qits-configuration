package eu.wohlben.qits.configuration.dto;

import java.time.Instant;

/**
 * One declaration in a listing: what it is and when it arrived, without the document itself.
 *
 * <p><b>{@code governing} is on the wire, and it is the field the listing exists for.</b> An
 * application accumulates versions and only one of them is what it is currently judged against —
 * decided by the intake log rather than by version ordering, which this service deliberately has no
 * opinion about. A listing that made a reader work that out from {@code receivedAt} would be a
 * listing that invited them to work it out wrong.
 *
 * <p>{@code keys} is a count rather than the keys: the listing is for choosing a version, and the
 * per-version read is one request away.
 */
public record DeclarationSummaryDto(
    String application,
    String version,
    String deploymentTarget,
    String contentHash,
    int keys,
    boolean governing,
    Instant receivedAt,
    String receivedBy) {}
