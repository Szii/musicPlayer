package org.dnd.group;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dnd.api.model.Group;
import org.dnd.api.model.GroupRequest;
import org.dnd.api.model.GroupTrackRef;
import org.dnd.api.model.GroupTrackRequest;
import org.dnd.api.model.ReorderGroupTracksRequest;
import org.dnd.board.BoardRepository;
import org.dnd.exception.BadRequestException;
import org.dnd.exception.ForbiddenException;
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

    List<GroupTrackRequest> items = request.getTracks();

    Map<UUID, TrackEntity> tracksById = trackRepository
            .findAllById(items.stream().map(GroupTrackRequest::getTrackId).collect(Collectors.toSet()))
            .stream()
            .collect(Collectors.toMap(TrackEntity::getId, track -> track));

    Set<UUID> windowIds = items.stream()
            .map(GroupTrackRequest::getWindowId)
            .filter(windowId -> windowId != null)
            .collect(Collectors.toSet());
    Map<UUID, TrackWindowEntity> windowsById = windowIds.isEmpty()
            ? Map.of()
            : trackWindowRepository.findAllById(windowIds).stream()
                    .collect(Collectors.toMap(TrackWindowEntity::getId, window -> window));

    Map<MembershipKey, String> desired = new LinkedHashMap<>();
    for (GroupTrackRequest item : items) {
      TrackEntity track = tracksById.get(item.getTrackId());
      if (track == null) {
        continue;
      }
      if (!validateTrackAccessForCurrentUser(track)) {
        throw new ForbiddenException(String.format("You can only add tracks you own. Track id %s is not accessible", track.getId()));
      }
      if (item.getWindowId() != null) {
        TrackWindowEntity window = windowsById.get(item.getWindowId());
        if (window == null || !window.getTrack().getId().equals(track.getId())) {
          throw new ForbiddenException(String.format("Window %s is not a window of track %s", item.getWindowId(), track.getId()));
        }
      }
      desired.put(new MembershipKey(item.getTrackId(), item.getWindowId()), item.getName());
    }

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

  @Transactional
  public Group reorderGroupTracks(UUID groupId, ReorderGroupTracksRequest request) {
    log.debug("Reordering items in group with id {}", groupId);

    GroupEntity group = groupRepository.findById(groupId)
            .orElseThrow(() -> new NotFoundException(String.format("Group with id %s not found", groupId)));

    if (!group.getOwner().getId().equals(securityUtils.getCurrentUserId())) {
      throw new ForbiddenException("You can only reorder items in your own groups");
    }

    List<GroupTrackRef> refs = request.getTracks();
    if (refs == null || refs.isEmpty()) {
      throw new BadRequestException("Items must not be empty");
    }

    List<MembershipKey> requestedKeys = refs.stream()
            .map(ref -> new MembershipKey(ref.getTrackId(), ref.getWindowId()))
            .toList();

    if (requestedKeys.size() != new HashSet<>(requestedKeys).size()) {
      throw new BadRequestException("Items must not contain duplicates");
    }

    Map<MembershipKey, GroupTrackEntity> byKey = group.getGroupTracks().stream()
            .collect(Collectors.toMap(GroupService::keyOf, groupTrack -> groupTrack));

    if (requestedKeys.size() != byKey.size()) {
      throw new BadRequestException("Request must contain all items of the group");
    }

    List<GroupTrackEntity> ordered = new ArrayList<>();
    for (MembershipKey key : requestedKeys) {
      GroupTrackEntity groupTrack = byKey.get(key);
      if (groupTrack == null) {
        throw new BadRequestException(String.format("Item %s does not belong to group %s", key, groupId));
      }
      ordered.add(groupTrack);
    }

    rewritePositionsSafely(ordered);

    return groupMapper.toDto(group);
  }

  private void rewritePositionsSafely(List<GroupTrackEntity> ordered) {
    for (int i = 0; i < ordered.size(); i++) {
      ordered.get(i).setPositionWithinGroup(-(i + 1));
    }
    groupRepository.flush();

    for (int i = 0; i < ordered.size(); i++) {
      ordered.get(i).setPositionWithinGroup(i + 1);
    }
    groupRepository.flush();
  }

  private static MembershipKey keyOf(GroupTrackEntity groupTrack) {
    UUID windowId = groupTrack.getTrackWindow() == null ? null : groupTrack.getTrackWindow().getId();
    return new MembershipKey(groupTrack.getTrack().getId(), windowId);
  }

  private record MembershipKey(UUID trackId, UUID windowId) {
  }

  private boolean validateTrackAccessForCurrentUser(TrackEntity track) {
    UserEntity user = userRepository.findById(securityUtils.getCurrentUserId())
            .orElseThrow(() -> new NotFoundException(String.format("User with id %s not found",securityUtils.getCurrentUserId())));
    return track.getOwner().getId().equals(securityUtils.getCurrentUserId()) ||
            (track.getTrackShare() != null && track.getTrackShare().getUsers().contains(user));
  }
}
