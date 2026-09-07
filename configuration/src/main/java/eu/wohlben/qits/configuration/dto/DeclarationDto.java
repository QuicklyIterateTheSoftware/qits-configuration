package eu.wohlben.qits.configuration.dto;

import java.time.Instant;
import java.util.List;

/**
 * One declaration in full: what this service parsed out of the document, AND the document.
 *
 * <p><b>Both halves, on purpose.</b> {@code keys} is what this service acts on and {@code raw} is
 * what the application committed, and the whole value of returning them together is that a person
 * can see whether the two agree — a {@code description:} line that reads one way and a {@code type:}
 * that behaves another is a discrepancy nobody would find by reading either half alone. The parsed
 * side is also the only place a reader can see what was DROPPED: {@code description} is validated,
 * carried in {@code raw}, and given no field of its own.
 */
public record DeclarationDto(
    String application,
    String version,
    String deploymentTarget,
    String contentHash,
    boolean governing,
    Instant receivedAt,
    String receivedBy,
    List<DeclaredKeyDto> keys,
    String raw) {}
