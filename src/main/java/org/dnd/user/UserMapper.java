package org.dnd.user;

import org.dnd.api.model.*;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface UserMapper {
  @Mapping(target = "limits", ignore = true)
  @Mapping(target = "managedByGoogle", ignore = true)
  User toDto(UserEntity entity);

  @Mapping(target = "id", source = "entity.id")
  @Mapping(target = "name", source = "entity.name")
  UserAuthDTO toAuthDto(UserEntity entity);

  UserLoginRequest fromUserChangePasswordRequest(UserChangePasswordRequest request);

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "password", ignore = true)
  @Mapping(target = "ownedTracks", ignore = true)
  @Mapping(target = "ownedGroups", ignore = true)
  @Mapping(target = "boards", ignore = true)
  UserEntity fromRegisterRequest(UserRegisterRequest request);

  UserInfoLite toLiteUserDto(UserEntity entity);

}
