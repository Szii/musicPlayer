package org.dnd.mappers;

import org.dnd.api.model.Track;
import org.dnd.api.model.TrackRequest;
import org.dnd.api.model.TrackShare;
import org.dnd.model.TrackEntity;
import org.mapstruct.*;

import java.util.List;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface TrackMapper {

    @Mapping(target = "group", ignore = true)
    @Mapping(target = "owner", ignore = true)
    @Mapping(target = "shares", ignore = true)
    TrackEntity toEntity(Track dto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "group", ignore = true)
    @Mapping(target = "owner", ignore = true)
    @Mapping(target = "duration", ignore = true)
    @Mapping(target = "trackPoints", ignore = true)
    @Mapping(target = "shares", ignore = true)
    TrackEntity toEntity(TrackRequest request);

    @Mapping(target = "ownerId", expression = "java(entity.getOwner() != null ? entity.getOwner().getId() : null)")
    @Mapping(target = "groupId", expression = "java(entity.getGroup() != null ? entity.getGroup().getId() : null)")
    Track toDto(TrackEntity entity);

    List<TrackEntity> toEntities(List<Track> dtos);

    List<Track> toDtos(List<TrackEntity> entities);


    TrackShare toShareDto(TrackEntity entity);


    @BeanMapping(nullValuePropertyMappingStrategy =
            NullValuePropertyMappingStrategy.IGNORE)
    void updateTrackFromRequest(
            TrackRequest request,
            @MappingTarget TrackEntity entity);
}


