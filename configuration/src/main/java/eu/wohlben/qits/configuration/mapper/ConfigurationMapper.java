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
