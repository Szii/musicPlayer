package org.dnd.session;

import org.dnd.api.model.SessionResponse;
import org.dnd.board.BoardMapper;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        uses = {BoardMapper.class}
)
public interface SessionMapper {
  @Mapping(target = "sessionId", source = "id")
  @Mapping(target = "sessionName", source = "name")
  @Mapping(target = "sessionDescription", source = "description")
  @Mapping(
          target = "trackIds",
          expression = "java(entity.getTracks().stream().map(org.dnd.track.TrackEntity::getId).toList())"
  )
  @Mapping(
          target = "groupIds",
          expression = "java(entity.getGroups().stream().map(org.dnd.group.GroupEntity::getId).toList())"
  )
  SessionResponse toResponse(SessionEntity entity);
}
