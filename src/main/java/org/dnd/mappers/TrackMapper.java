package org.dnd.mappers;

import org.dnd.api.model.Track;
import org.dnd.api.model.TrackRequest;
import org.dnd.api.model.User;
import org.dnd.model.TrackEntity;
import org.dnd.model.UserEntity;
import org.mapstruct.*;

import java.util.List;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface TrackMapper {
    @Mapping(target = "groups", ignore = true)
    @Mapping(target = "owner", ignore = true)
    @Mapping(target = "trackWindows", ignore = true)
    TrackEntity toEntity(Track dto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "groups", ignore = true)
    @Mapping(target = "owner", ignore = true)
    @Mapping(target = "trackWindows", ignore = true)
    TrackEntity toEntity(TrackRequest request);

    @Mapping(target = "ownerId", expression = "java(entity.getOwner() != null ? entity.getOwner().getId() : null)")
    @Mapping(target = "groupIds", expression = "java(entity.getGroups() == null ? java.util.List.of() : entity.getGroups().stream().map(org.dnd.model.GroupEntity::getId).toList())")
    Track toDto(TrackEntity entity);

    List<TrackEntity> toEntities(List<Track> dtos);

    List<Track> toDtos(List<TrackEntity> entities);

    default User toUserDto(UserEntity entity) {
        if (entity == null) return null;
        return new User()
                .id(entity.getId())
                .name(entity.getName());
    }

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "groups", ignore = true)
    @Mapping(target = "owner", ignore = true)
    @Mapping(target = "trackWindows", ignore = true)
    void updateTrackFromRequest(TrackRequest request, @MappingTarget TrackEntity entity);
}



