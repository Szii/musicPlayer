package org.dnd.group;

import org.dnd.api.model.Group;
import org.dnd.api.model.Track;
import org.dnd.track.TrackEntity;
import org.dnd.track.TrackMapper;
import org.dnd.user.UserMapper;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.util.List;
import java.util.Set;

@Mapper(
        componentModel = "spring",
        uses = {TrackMapper.class, UserMapper.class},
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface GroupMapper {

  @Mapping(target = "owner", ignore = true)
  @Mapping(target = "groupTracks", ignore = true)
  GroupEntity toEntity(Group dto);

  @Mapping(target = "tracks", source = "groupTracks")
  Group toDto(GroupEntity entity);

  List<Group> toDtos(List<GroupEntity> entities);

  Track toTrackDto(TrackEntity track);

  default List<Track> toTrackDtos(Set<GroupTrackEntity> groupTracks) {
    if (groupTracks == null) {
      return List.of();
    }
    return groupTracks.stream()
            .map(groupTrack -> {
              Track track = toTrackDto(groupTrack.getTrack());
              if (groupTrack.getCustomName() != null) {
                track.setTrackName(groupTrack.getCustomName());
              }
              return track;
            })
            .toList();
  }
}
