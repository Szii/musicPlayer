package org.dnd.mappers;

import org.dnd.api.model.Group;
import org.dnd.api.model.GroupShare;
import org.dnd.model.GroupEntity;
import org.dnd.model.UserGroupShareEntity;
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
    @Mapping(target = "shares", ignore = true)
    GroupEntity toEntity(Group dto);

    @Mapping(target = "ownerId", expression = "java(entity.getOwner() != null ? entity.getOwner().getId() : null)")
    Group toDto(GroupEntity entity);

    List<GroupEntity> toEntities(List<Group> dtos);

    List<Group> toDtos(List<GroupEntity> entities);

    GroupShare toShareDto(GroupEntity entity);

    @Mapping(target = "user", source = "user")
    GroupShare toShareDto(UserGroupShareEntity share);

}