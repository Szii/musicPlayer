package org.dnd.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dnd.api.model.Group;
import org.dnd.api.model.GroupRequest;
import org.dnd.exception.ForbiddenException;
import org.dnd.exception.NotFoundException;
import org.dnd.mappers.GroupMapper;
import org.dnd.model.GroupEntity;
import org.dnd.model.TrackEntity;
import org.dnd.model.UserEntity;
import org.dnd.repository.GroupRepository;
import org.dnd.repository.TrackRepository;
import org.dnd.repository.UserRepository;
import org.dnd.utils.SecurityUtils;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class GroupService {
    private final GroupRepository groupRepository;
    private final UserRepository userRepository;
    private final GroupMapper groupMapper;
    private final TrackRepository trackRepository;

    public List<Group> getUserGroups() {
        Long userId = SecurityUtils.getCurrentUserId();
        log.debug("Getting groups for user with id {}", userId);
        return groupMapper.toDtos(groupRepository.findAccessibleGroupsForUser(userId));
    }

    public Group createGroup(GroupRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        log.debug("Creating group with name {}", request.getListName());
        UserEntity owner = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException(String.format("User with id %d not found", userId)));

        GroupEntity group = new GroupEntity();
        group.setListName(request.getListName());
        group.setOwner(owner);

        return groupMapper.toDto(groupRepository.save(group));
    }

    public void deleteGroup(Long groupId) {
        log.debug("Deleting group with id {}", groupId);

        GroupEntity group = groupRepository.findById(groupId)
                .orElseThrow(() -> new NotFoundException(String.format("Group with id %d not found", groupId)));

        if (!group.getOwner().getId().equals(SecurityUtils.getCurrentUserId())) {
            throw new ForbiddenException("You can only delete your own groups");
        }

        groupRepository.deleteById(groupId);
    }

    public Group updateGroup(Long groupId, GroupRequest request) {
        log.debug("Updating group with id {}", groupId);
        GroupEntity group = groupRepository.findById(groupId)
                .orElseThrow(() -> new NotFoundException(String.format("Group with id %d not found", groupId)));

        if (!group.getOwner().getId().equals(SecurityUtils.getCurrentUserId())) {
            throw new ForbiddenException("You can only update your own groups");
        }

        List<TrackEntity> tracks = trackRepository.findAllById(request.getTrackIds());

        tracks.forEach(track -> {
            if (!validateTrackAccessForCurrentUser(track)) {
                throw new ForbiddenException(String.format("You can only add tracks you own. Track id %d is not accessible", track.getId()));
            }
        });

        group.setListName(request.getListName());
        group.setTracks(new HashSet<>(tracks));
        return groupMapper.toDto(groupRepository.save(group));
    }

    private boolean validateTrackAccessForCurrentUser(TrackEntity track) {
        UserEntity user = userRepository.findById(SecurityUtils.getCurrentUserId())
                .orElseThrow(() -> new NotFoundException(String.format("User with id %d not found", SecurityUtils.getCurrentUserId())));
        return track.getOwner().getId().equals(SecurityUtils.getCurrentUserId()) ||
                (track.getTrackShare() != null && track.getTrackShare().getUsers().contains(user));
    }
}
