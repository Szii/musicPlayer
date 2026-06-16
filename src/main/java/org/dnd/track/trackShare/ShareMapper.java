package org.dnd.track.trackShare;

import org.dnd.api.model.TrackShareResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        uses = {}
)
public interface ShareMapper {

  @Mapping(
          target = "subscriberCount",
          expression = "java(entity.getUsers().size())"
  )
  TrackShareResponse toResponse(TrackShareEntity entity);
}
