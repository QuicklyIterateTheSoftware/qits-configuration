package eu.wohlben.qits.configuration.mapper;

import eu.wohlben.qits.configuration.dto.ConfigurationEntryDto;
import eu.wohlben.qits.configuration.dto.ConfigurationRevisionDto;
import eu.wohlben.qits.configuration.entity.ConfigurationEntry;
import eu.wohlben.qits.configuration.entity.ConfigurationRevision;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Entities to wire shapes. The three renamed fields ({@code entryKey}, {@code entryValue}, {@code
 * entryClass}) are mapped explicitly — MapStruct would not guess them, and a silent null on a wire
 * field is the failure this mapping exists to make impossible.
 *
 * <p>{@code env} carries no {@code @Mapping} because the field and the wire component are the same
 * word — the ordinary case, and the reason the list above is short rather than exhaustive. An
 * unmapped component is a compiler WARNING from the processor and not an error, so the guard against
 * a forgotten one is the suite: every wire field this API returns is asserted somewhere, because a
 * silent null on the wire is the one failure a mapper cannot be trusted to announce.
 */
@Mapper(componentModel = "jakarta")
public interface ConfigurationMapper {

  /**
   * <b>Two source parameters, because one wire field is not on the entity at all.</b> {@code
   * orphaned} is decided by the application's governing declaration and not by the row — the same
   * row is orphaned or not depending on a document nobody touched when it was written — so it is
   * computed by {@code ConfigurationService} and handed in beside the entity rather than persisted.
   * A single-argument mapping would have left it silently false, which is the failure this mapper
   * exists to make impossible.
   */
  @Mapping(target = "key", source = "entity.entryKey")
  @Mapping(target = "value", source = "entity.entryValue")
  @Mapping(target = "revision", source = "entity.headRevision")
  @Mapping(target = "orphaned", source = "orphaned")
  ConfigurationEntryDto toDto(ConfigurationEntry entity, boolean orphaned);

  @Mapping(target = "key", source = "entryKey")
  @Mapping(target = "value", source = "entryValue")
  ConfigurationRevisionDto toDto(ConfigurationRevision entity);
}
