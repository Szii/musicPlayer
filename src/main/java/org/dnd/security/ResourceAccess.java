package org.dnd.security;

import lombok.RequiredArgsConstructor;
import org.dnd.board.BoardRepository;
import org.dnd.exception.NotFoundException;
import org.dnd.group.GroupRepository;
import org.dnd.track.TrackRepository;
import org.dnd.utils.SecurityUtils;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

@Component("resourceAccess")
@RequiredArgsConstructor
public class ResourceAccess {

  private final TrackRepository trackRepository;
  private final GroupRepository groupRepository;
  private final BoardRepository boardRepository;
  private final SecurityUtils securityUtils;

  public boolean isTrackOwner(UUID trackId) {
    if (!trackRepository.existsByIdAndOwner_Id(trackId, securityUtils.getCurrentUserId())) {
      throw new NotFoundException(String.format("Track with id %s not found", trackId));
    }
    return true;
  }

  public boolean isGroupOwner(UUID groupId) {
    if (!groupRepository.existsByIdAndOwner_Id(groupId, securityUtils.getCurrentUserId())) {
      throw new NotFoundException(String.format("Group with id %s not found", groupId));
    }
    return true;
  }

  public boolean isBoardOwner(UUID boardId) {
    if (!boardRepository.existsByIdAndOwner_Id(boardId, securityUtils.getCurrentUserId())) {
      throw new NotFoundException(String.format("Board with id %s not found", boardId));
    }
    return true;
  }

  public boolean canAccessTracks(Collection<UUID> trackIds) {
    if (trackIds == null || trackIds.isEmpty()) {
      return true;
    }
    Set<UUID> ids = Set.copyOf(trackIds);
    if (trackRepository.countAccessibleByIdsAndUserId(ids, securityUtils.getCurrentUserId()) != ids.size()) {
      throw new NotFoundException("One or more tracks not found");
    }
    return true;
  }
}
