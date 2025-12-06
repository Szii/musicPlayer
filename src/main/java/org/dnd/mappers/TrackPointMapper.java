package org.dnd.mappers;

import org.dnd.api.model.TrackPoint;
import org.dnd.api.model.TrackPointRequest;
import org.dnd.model.TrackEntity;
import org.dnd.model.TrackPointEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring")
public interface TrackPointMapper {

    @Mapping(target = "track", ignore = true)
    TrackPointEntity toEntity(TrackPoint dto);

    TrackPoint toDto(TrackPointEntity entity);

    List<TrackPointEntity> toEntities(List<TrackPoint> dtos);

    List<TrackPoint> toDtos(List<TrackPointEntity> entities);

    @Mapping(target = "id", ignore = true)

    @Mapping(target = "track", source = "entity")
    TrackPointEntity fromRequest(TrackPointRequest dto, TrackEntity entity);

    @Mapping(target = "track", ignore = true)
    @Mapping(target = "id", ignore = true)
    TrackPointEntity updateFromRequest(TrackPointRequest request, @MappingTarget TrackPointEntity target);

}
