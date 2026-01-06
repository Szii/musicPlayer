package org.dnd.mappers;

import org.dnd.api.model.TrackWindow;
import org.dnd.api.model.TrackWindowRequest;
import org.dnd.model.TrackEntity;
import org.dnd.model.TrackWindowEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring")
public interface TrackWindowMapper {

    @Mapping(target = "track", ignore = true)
    TrackWindowEntity toEntity(TrackWindow dto);

    TrackWindow toDto(TrackWindowEntity entity);

    List<TrackWindowEntity> toEntities(List<TrackWindow> dtos);

    List<TrackWindow> toDtos(List<TrackWindowEntity> entities);

    @Mapping(target = "id", ignore = true)

    @Mapping(target = "track", source = "entity")
    TrackWindowEntity fromRequest(TrackWindowRequest dto, TrackEntity entity);

    @Mapping(target = "track", ignore = true)
    @Mapping(target = "id", ignore = true)
    TrackWindowEntity updateFromRequest(TrackWindowRequest request, @MappingTarget TrackWindowEntity target);

}
