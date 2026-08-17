package eu.wohlben.qits.configuration.dto;

import java.util.Map;

/**
 * The deployer-facing read: one application's whole configuration, at the property NAMES a consumer
 * layers verbatim ({@code qits.platform.deployments.extras.<app>.<key>}).
 *
 * <p>{@code headRevision} is what the consumer records to say which configuration it deployed with.
 * It is read from the append-only log rather than from the entries, so it never moves backwards.
 */
public record ResolvedConfigurationDto(long headRevision, Map<String, String> properties) {}
