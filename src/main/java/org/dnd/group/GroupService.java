package org.dnd.group;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dnd.api.model.Group;
import org.dnd.api.model.GroupRequest;
import org.dnd.api.model.GroupTrackRequest;
import org.dnd.board.BoardRepository;
import org.dnd.exception.ForbiddenException;
import org.dnd.exception.LimitReachedException;
import org.dnd.exception.NotFoundException;
import org.dnd.track.TrackEntity;
import org.dnd.track.TrackRepository;
import org.dnd.user.UserEntity;
import org.dnd.user.UserRepository;
import org.dnd.user.rank.UserRankEvaluatorService;
import org.dnd.utils.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class GroupService {
  private final GroupRepository groupRepository;
  private final UserRepository userRepository;
  private final GroupMapper groupMapper;
  private final TrackRepository trackRepository;
  private final BoardRepository boardRepository;
  private final UserRankEvaluatorService userRankEvaluatorService;
  private final SecurityUtils securityUtils;

  @Transactional(readOnly = true)
  public List<Group> getUserGroups() {
    UUID userId = securityUtils.getCurrentUserId();
    log.debug("Getting groups for user with id {}", userId);
    return groupMapper.toDtos(groupRepository.findAccessibleGroupsForUser(userId));
  }

  @Transactional
  public Group createGroup(GroupRequest request) {
    UUID userId = securityUtils.getCurrentUserId();
    log.debug("Creating group with name {}", request.getListName());
    UserEntity owner = userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException(String.format("User with id %s not found",userId)));

    if (!userRankEvaluatorService.canCreateGroup(owner)) {
      throw new LimitReachedException("Group limit reached");
    }

    GroupEntity group = new GroupEntity();
    group.setListName(request.getListName());
    group.setOwner(owner);

    return groupMapper.toDto(groupRepository.save(group));
  }

  @Transactional
  public void deleteGroup(UUID groupId) {
    log.debug("Deleting group with id {}", groupId);

    GroupEntity group = groupRepository.findById(groupId)
            .orElseThrow(() -> new NotFoundException(
                    String.format("Group with id %s not found", groupId)));

    if (!group.getOwner().getId().equals(securityUtils.getCurrentUserId())) {
      throw new ForbiddenException("You can only delete your own groups");
    }

    boardRepository.clearSelectedGroupFromBoards(groupId);

    group.getGroupTracks().clear();

    groupRepository.delete(group);
  }

  @Transactional
  public Group updateGroup(UUID groupId, GroupRequest request) {
    log.debug("Updating group with id {}", groupId);
    GroupEntity group = groupRepository.findById(groupId)
            .orElseThrow(() -> new NotFoundException(String.format("Group with id %s not found", groupId)));

    if (!group.getOwner().getId().equals(securityUtils.getCurrentUserId())) {
      throw new ForbiddenException("You can only update your own groups");
    }

    Map<UUID, String> nameByTrackId = new LinkedHashMap<>();
    request.getTracks().forEach(track -> nameByTrackId.put(track.getTrackId(), track.getName()));

    List<TrackEntity> tracks = trackRepository.findAllById(nameByTrackId.keySet());

    tracks.forEach(track -> {
      if (!validateTrackAccessForCurrentUser(track)) {
        throw new ForbiddenException(String.format("You can only add tracks you own. Track id %s is not accessible", track.getId()));
      }
    });

    group.setListName(request.getListName());

    group.getGroupTracks().removeIf(groupTrack ->
            !nameByTrackId.containsKey(groupTrack.getTrack().getId()));

    Set<UUID> existingTrackIds = new HashSet<>();
    group.getGroupTracks().forEach(groupTrack -> {
      groupTrack.setCustomName(nameByTrackId.get(groupTrack.getTrack().getId()));
      existingTrackIds.add(groupTrack.getTrack().getId());
    });

    tracks.stream()
            .filter(track -> !existingTrackIds.contains(track.getId()))
            .forEach(track -> group.addTrack(track, nameByTrackId.get(track.getId())));

    return groupMapper.toDto(groupRepository.save(group));
  }

  private boolean validateTrackAccessForCurrentUser(TrackEntity track) {
    UserEntity user = userRepository.findById(securityUtils.getCurrentUserId())
            .orElseThrow(() -> new NotFoundException(String.format("User with id %s not found",securityUtils.getCurrentUserId())));
    return track.getOwner().getId().equals(securityUtils.getCurrentUserId()) ||
            (track.getTrackShare() != null && track.getTrackShare().getUsers().contains(user));
  }
}
