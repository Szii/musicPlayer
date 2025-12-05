package org.dnd.mappers;

import org.dnd.api.model.TrackPoint;
import org.dnd.model.TrackPointEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface TrackPointMapper {

    @Mapping(target = "track", ignore = true)
    TrackPointEntity toEntity(TrackPoint dto);

    TrackPoint toDto(TrackPointEntity entity);

    List<TrackPointEntity> toEntities(List<TrackPoint> dtos);

    List<TrackPoint> toDtos(List<TrackPointEntity> entities);
}
