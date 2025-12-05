package org.dnd.mappers;

import org.dnd.api.model.User;
import org.dnd.api.model.UserRegisterRequest;
import org.dnd.model.UserEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface UserMapper {
    User toDto(UserEntity entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "tracks", ignore = true)
    @Mapping(target = "groups", ignore = true)
    @Mapping(target = "boards", ignore = true)
    @Mapping(target = "trackAccesses", ignore = true)
    UserEntity fromRegisterRequest(UserRegisterRequest request);
}
