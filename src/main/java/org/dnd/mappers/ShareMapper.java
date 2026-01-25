package org.dnd.mappers;

import org.dnd.api.model.TrackShareResponse;
import org.dnd.model.TrackShareEntity;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        uses = {TrackMapper.class}
)
public interface ShareMapper {

    TrackShareResponse toResponse(TrackShareEntity entity);

    TrackShareEntity toEntity(TrackShareResponse response);

}
