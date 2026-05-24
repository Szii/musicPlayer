package org.dnd.track.trackShare;

import org.dnd.api.model.TrackShareResponse;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        uses = {}
)
public interface ShareMapper {

  TrackShareResponse toResponse(TrackShareEntity entity);
}
