package org.dnd.mappers;

import org.dnd.api.model.Track;
import org.dnd.api.model.TrackRequest;
import org.dnd.model.TrackEntity;
import org.mapstruct.*;

import java.util.List;

@Mapper(
        componentModel = "spring",
        uses = {UserMapper.class, ShareMapper.class},
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface TrackMapper {
  @Mapping(target = "groups", ignore = true)
  @Mapping(target = "owner", ignore = true)
  @Mapping(target = "trackWindows", ignore = true)
  TrackEntity toEntity(Track dto);

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "groups", ignore = true)
  @Mapping(target = "owner", ignore = true)
  @Mapping(target = "trackWindows", ignore = true)
  TrackEntity toEntity(TrackRequest request);

  @Mapping(
          target = "groupIds",
          expression = "java(entity.getGroups() == null ? java.util.List.of() : entity.getGroups().stream().map(org.dnd.model.GroupEntity::getId).toList())"
  )
  @Mapping(
          target = "owned",
          expression = "java(entity.getOwner().getId().equals(userId))"
  )
  Track toDto(TrackEntity entity, Long userId);

  List<TrackEntity> toEntities(List<Track> dtos);

  List<Track> toDtos(List<TrackEntity> entities);

  @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
  @Mapping(target = "groups", ignore = true)
  @Mapping(target = "owner", ignore = true)
  @Mapping(target = "trackWindows", ignore = true)
  void updateTrackFromRequest(TrackRequest request, @MappingTarget TrackEntity entity);
}



