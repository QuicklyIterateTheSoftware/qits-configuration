package eu.wohlben.qits.configuration.dto;

/**
 * One application in the listing: how many entries it currently has, and how far its history has
 * run.
 *
 * <p>{@code headRevision} comes from the LOG, so it keeps moving forward when an entry is deleted
 * and {@code entries} goes down.
 */
public record ApplicationSummaryDto(String application, int entries, long headRevision) {}
