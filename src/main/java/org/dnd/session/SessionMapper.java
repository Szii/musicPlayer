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
  SessionResponse toResponse(SessionEntity entity);
}
