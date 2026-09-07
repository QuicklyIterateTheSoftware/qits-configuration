package eu.wohlben.qits.configuration.dto;

import java.util.List;

/**
 * One application in the listing, ACROSS every environment this store holds it in.
 *
 * <p><b>The counts hang off the envs, not off the application.</b> On a platform instance an
 * application is configured once per tier, and a single entry count over all of them would be a
 * number nobody can act on — "qits-gateway has 11 entries" says nothing about whether prod is
 * missing the one dev has. The per-env rows are the answer, and having them side by side is the
 * whole reason this service was promoted onto the platform plane.
 *
 * <p>{@code envs} is sorted by env name and is never empty: an application appears in this listing
 * because some env has something to say about it.
 */
public record ApplicationSummaryDto(String application, List<ApplicationEnvSummaryDto> envs) {}
