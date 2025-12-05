package org.dnd.mappers;

import org.dnd.api.model.Group;
import org.dnd.model.GroupEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(
        componentModel = "spring",
        uses = {TrackMapper.class},
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface GroupMapper {

    @Mapping(target = "owner", ignore = true)
    @Mapping(target = "tracks", ignore = true)
    @Mapping(target = "userAccesses", ignore = true)
    GroupEntity toEntity(Group dto);

    @Mapping(target = "ownerId", expression = "java(entity.getOwner() != null ? entity.getOwner().getId() : null)")
    Group toDto(GroupEntity entity);

    List<GroupEntity> toEntities(List<Group> dtos);

    List<Group> toDtos(List<GroupEntity> entities);
}