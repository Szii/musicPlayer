package org.dnd.mappers;

import org.dnd.api.model.Board;
import org.dnd.model.BoardEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface BoardMapper {

    @Mapping(target = "selectedTrack", ignore = true)
    BoardEntity toEntity(Board dto);

    @Mapping(target = "selectedTrack", source = "boardEntity.selectedTrack")
    Board toDto(BoardEntity boardEntity);

    List<BoardEntity> toEntities(List<Board> dtos);

    List<Board> toBoardDtos(List<BoardEntity> entities);
}



