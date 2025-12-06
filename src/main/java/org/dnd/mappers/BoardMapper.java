package org.dnd.mappers;

import org.dnd.api.model.Board;
import org.dnd.api.model.BoardCreateRequest;
import org.dnd.api.model.BoardUpdateRequest;
import org.dnd.model.BoardEntity;
import org.mapstruct.*;

import java.util.List;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        uses = {TrackMapper.class}
)
public interface BoardMapper {
    @Mapping(target = "owner", ignore = true)
    @Mapping(target = "selectedTrack", ignore = true)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "currentPosition", constant = "0")
    BoardEntity toEntity(BoardCreateRequest request);

    @Mapping(target = "ownerId", expression = "java(entity.getOwner().getId())")
    @Mapping(target = "userId", expression = "java(entity.getOwner().getId())")
    Board toDto(BoardEntity entity);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "owner", ignore = true)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "currentPosition", ignore = true)
    @Mapping(target = "selectedTrack.owner", ignore = true)
    @Mapping(target = "selectedTrack.group", ignore = true)
    @Mapping(target = "selectedTrack.userAccesses", ignore = true)
    void updateBoardFromRequest(BoardUpdateRequest request, @MappingTarget BoardEntity entity);

    List<Board> toDtos(List<BoardEntity> entities);
}





