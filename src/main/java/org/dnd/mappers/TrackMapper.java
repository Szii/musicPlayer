package org.dnd.mappers;

import org.dnd.api.model.Track;
import org.dnd.model.TrackEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface TrackMapper {

    @Mapping(target = "group", ignore = true)
    @Mapping(target = "owner", ignore = true)
    @Mapping(target = "userAccesses", ignore = true)
    TrackEntity toEntity(Track dto);

    @Mapping(target = "ownerId", expression = "java(entity.getOwner() != null ? entity.getOwner().getId() : null)")
    @Mapping(target = "groupId", expression = "java(entity.getGroup() != null ? entity.getGroup().getId() : null)")
    Track toDto(TrackEntity entity);

    List<TrackEntity> toEntities(List<Track> dtos);

    List<Track> toDtos(List<TrackEntity> entities);
}


