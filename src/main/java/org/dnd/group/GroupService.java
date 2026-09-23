package org.dnd.group;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dnd.api.model.Group;
import org.dnd.api.model.GroupRequest;
import org.dnd.api.model.GroupTrackRef;
import org.dnd.api.model.GroupTrackRequest;
import org.dnd.api.model.ReorderGroupTracksRequest;
import org.dnd.board.BoardRepository;
import org.dnd.exception.LimitReachedException;
import org.dnd.exception.NotFoundException;
import org.dnd.track.TrackEntity;
import org.dnd.track.TrackRepository;
import org.dnd.track.TrackWindowEntity;
import org.dnd.track.TrackWindowRepository;
import org.dnd.user.UserEntity;
import org.dnd.user.UserRepository;
import org.dnd.user.rank.UserRankEvaluatorService;
import org.dnd.utils.SecurityUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class GroupService {
  private final GroupRepository groupRepository;
  private final UserRepository userRepository;
  private final GroupMapper groupMapper;
  private final TrackRepository trackRepository;
  private final TrackWindowRepository trackWindowRepository;
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

  @PreAuthorize("@resourceAccess.isGroupOwner(#groupId)")
  @Transactional
  public void deleteGroup(UUID groupId) {
    log.debug("Deleting group with id {}", groupId);

    GroupEntity group = groupRepository.findById(groupId)
            .orElseThrow(() -> new NotFoundException(
                    String.format("Group with id %s not found", groupId)));

    boardRepository.clearSelectedGroupFromBoards(groupId);

    group.getGroupTracks().clear();

    groupRepository.delete(group);
  }

  @PreAuthorize("@resourceAccess.isGroupOwner(#groupId) and @resourceAccess.canAccessTracks(#request.tracks.![trackId])")
  @Transactional
  public Group updateGroup(UUID groupId, GroupRequest request) {
    log.debug("Updating group with id {}", groupId);
    GroupEntity group = groupRepository.findById(groupId)
            .orElseThrow(() -> new NotFoundException(String.format("Group with id %s not found", groupId)));

    Map<UUID, String> nameByTrackId = new LinkedHashMap<>();
    request.getTracks().forEach(track -> nameByTrackId.put(track.getTrackId(), track.getName()));

    List<TrackEntity> tracks = trackRepository.findAllById(nameByTrackId.keySet());

    group.setListName(request.getListName());

    group.getGroupTracks().removeIf(groupTrack -> !desired.containsKey(keyOf(groupTrack)));

    List<GroupTrackEntity> ordered = group.getGroupTracks().stream()
            .sorted(Comparator.comparingInt(GroupTrackEntity::getPositionWithinGroup))
            .collect(Collectors.toCollection(ArrayList::new));

    Set<MembershipKey> existing = new HashSet<>();
    ordered.forEach(groupTrack -> {
      groupTrack.setCustomName(desired.get(keyOf(groupTrack)));
      existing.add(keyOf(groupTrack));
    });

    desired.forEach((key, name) -> {
      if (!existing.contains(key)) {
        TrackWindowEntity window = key.windowId() == null ? null : windowsById.get(key.windowId());
        ordered.add(group.addTrack(tracksById.get(key.trackId()), window, name));
      }
    });

    rewritePositionsSafely(ordered);

    return groupMapper.toDto(groupRepository.save(group));
  }
}
