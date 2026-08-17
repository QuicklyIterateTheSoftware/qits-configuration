package eu.wohlben.qits.configuration.dto;

/**
 * What a bulk import did.
 *
 * <ul>
 *   <li>{@code imported} — lines that wrote a revision, because the key was new or its value
 *       changed.
 *   <li>{@code unchanged} — lines whose value was already exactly that. They write nothing, which is
 *       what makes re-running an import free and keeps the history a record of changes rather than
 *       of runs.
 *   <li>{@code ignored} — lines this service has no business with: comments, blanks, and any
 *       property outside the extras prefix. A real properties file carries all three, so an import
 *       that refused them would be an import nobody could run.
 * </ul>
 */
public record ImportSummaryDto(int imported, int unchanged, int ignored) {}
