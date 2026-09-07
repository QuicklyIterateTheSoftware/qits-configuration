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

  @Mapping(target = "key", source = "entryKey")
  @Mapping(target = "value", source = "entryValue")
  @Mapping(target = "revision", source = "headRevision")
  ConfigurationEntryDto toDto(ConfigurationEntry entity);

  @Mapping(target = "key", source = "entryKey")
  @Mapping(target = "value", source = "entryValue")
  ConfigurationRevisionDto toDto(ConfigurationRevision entity);
}
