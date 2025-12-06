package org.dnd.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dnd.api.model.Group;
import org.dnd.api.model.GroupRequest;
import org.dnd.exception.NotFoundException;
import org.dnd.mappers.GroupMapper;
import org.dnd.model.GroupEntity;
import org.dnd.model.UserEntity;
import org.dnd.repository.GroupRepository;
import org.dnd.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class GroupService {
    private final GroupRepository groupRepository;
    private final UserRepository userRepository;
    private final GroupMapper groupMapper;

    public List<Group> getUserGroups(Long userId) {
        log.debug("Getting groups for user with id {}", userId);
        return groupMapper.toDtos(groupRepository.findAccessibleGroupsForUser(userId));
    }

    public Group createGroup(GroupRequest request) {
        log.debug("Creating group with name {}", request.getListName());
        UserEntity owner = userRepository.findById(request.getOwnerId())
                .orElseThrow(() -> new NotFoundException(String.format("User with id %d not found", request.getOwnerId())));

        GroupEntity group = new GroupEntity();
        group.setListName(request.getListName());
        group.setOwner(owner);

        return groupMapper.toDto(groupRepository.save(group));
    }

    public void deleteGroup(Long groupId) {
        log.debug("Deleting group with id {}", groupId);
        if (!groupRepository.existsById(groupId)) {
            throw new NotFoundException(String.format("Group with id %d not found", groupId));
        }
        groupRepository.deleteById(groupId);
    }

    public Group updateGroup(Long groupId, GroupRequest request) {
        log.debug("Updating group with id {}", groupId);
        GroupEntity group = groupRepository.findById(groupId)
                .orElseThrow(() -> new NotFoundException(String.format("Group with id %d not found", groupId)));

        group.setListName(request.getListName());
        return groupMapper.toDto(groupRepository.save(group));
    }
}
