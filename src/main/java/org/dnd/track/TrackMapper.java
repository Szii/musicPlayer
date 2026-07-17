package org.dnd.track;

import org.dnd.api.model.CreateTrackRequestV2;
import org.dnd.api.model.Track;
import org.dnd.api.model.TrackRequest;
import org.dnd.api.model.UpdateTrackRequestV2;
import org.dnd.track.trackShare.ShareMapper;
import org.dnd.user.UserMapper;
import org.mapstruct.*;

import java.util.List;
import java.util.UUID;

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

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "groups", ignore = true)
  @Mapping(target = "owner", ignore = true)
  @Mapping(target = "trackWindows", ignore = true)
  TrackEntity toEntity(CreateTrackRequestV2 request);

  @Mapping(
          target = "groupIds",
          expression = "java(entity.getGroups() == null ? java.util.List.of() : entity.getGroups().stream().map(org.dnd.group.GroupEntity::getId).toList())"
  )
  @Mapping(
          target = "owned",
          expression = "java(entity.getOwner().getId().equals(userId))"
  )
  Track toDto(TrackEntity entity, UUID userId);

  List<TrackEntity> toEntities(List<Track> dtos);

  List<Track> toDtos(List<TrackEntity> entities);

  @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
  @Mapping(target = "groups", ignore = true)
  @Mapping(target = "owner", ignore = true)
  @Mapping(target = "trackWindows", ignore = true)
  void updateTrackFromRequest(UpdateTrackRequestV2 request, @MappingTarget TrackEntity entity);
}
