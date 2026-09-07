package eu.wohlben.qits.configuration.dto;

/**
 * One application's configuration in ONE environment: how many entries it currently has there, and
 * how far that environment's history of it has run.
 *
 * <p>{@code headRevision} comes from the LOG and is scoped to this env, so it keeps moving forward
 * when an entry is deleted and {@code entries} goes down — and a write in another env does not move
 * it at all. Two envs of one application are two independent histories; a single number over both
 * would tell a deployer its configuration had changed every time somebody edited a different tier.
 */
public record ApplicationEnvSummaryDto(String env, int entries, long headRevision) {}
