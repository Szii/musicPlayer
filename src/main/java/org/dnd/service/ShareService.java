package org.dnd.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dnd.api.model.Track;
import org.dnd.api.model.TrackShareResponse;
import org.dnd.exception.ForbiddenException;
import org.dnd.exception.NotFoundException;
import org.dnd.mappers.ShareMapper;
import org.dnd.mappers.TrackMapper;
import org.dnd.model.GroupEntity;
import org.dnd.model.TrackEntity;
import org.dnd.model.TrackShareEntity;
import org.dnd.model.UserEntity;
import org.dnd.repository.*;
import org.dnd.utils.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class ShareService {

  private final TrackShareRepository trackShareRepository;
  private final TrackRepository trackRepository;
  private final UserRepository userRepository;
  private final BoardRepository boardRepository;
  private final GroupRepository groupRepository;
  private final TrackMapper trackMapper;
  private final ShareMapper shareMapper;


  @Transactional
  public TrackShareResponse publish(Long trackId, String description) {
    TrackEntity track = trackRepository.findById(trackId)
            .orElseThrow(() -> new NotFoundException("Track not found with id: " + trackId));

    if (!track.getOwner().getId().equals(SecurityUtils.getCurrentUserId())) {
      throw new ForbiddenException("You can only publish tracks you own");
    }
    if (track.getTrackShare() != null) {
      throw new NotFoundException("Track is already published");
    }

    TrackShareEntity share = new TrackShareEntity();
    share.setDescription(description);
    share.setShareCode(generateUniqueShareCode().toString());

    track.setTrackShare(share);
    share.setTrack(track);

    return shareMapper.toResponse(trackShareRepository.save(share));
  }


  @Transactional
  public void subscribe(String shareCode) {
    Long userId = SecurityUtils.getCurrentUserId();
    TrackShareEntity trackShare = trackShareRepository.findByShareCode(shareCode)
            .orElseThrow(() -> new NotFoundException("Invalid share code: " + shareCode));

    UserEntity user = userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException("User not found with id: " + userId));

    user.getShares().add(trackShare);
    trackShare.getUsers().add(user);
    userRepository.save(user);

    log.info("User {} subscribed to track {} via workshop", userId, trackShare.getTrack().getId());
  }

  @Transactional
  public void unsubscribe(Long trackId) {
    Long userId = SecurityUtils.getCurrentUserId();

    UserEntity user = userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException("User not found with id: " + userId));

    TrackEntity track = trackRepository.findById(trackId)
            .orElseThrow(() -> new NotFoundException("Track not found with id: " + trackId));

    TrackShareEntity share = track.getTrackShare();
    if (share == null) {
      throw new NotFoundException("Track is not published");
    }

    if (!user.getShares().contains(share)) {
      throw new NotFoundException("You are not subscribed to this track");
    }

    user.getShares().remove(share);
    share.getUsers().remove(user);

    removeTrackFromGroupsOwnedByUser(trackId, userId);
    boardRepository.clearSelectedTrackFromBoardsOwnedByUser(trackId, userId);

    log.info("User {} unsubscribed from track {}", userId, trackId);
  }

  @Transactional
  public void unpublish(Long trackId) {
    TrackEntity track = trackRepository.findById(trackId)
            .orElseThrow(() -> new NotFoundException("Track not found with id: " + trackId));

    if (!track.getOwner().getId().equals(SecurityUtils.getCurrentUserId())) {
      throw new ForbiddenException("You can only unpublish tracks you own");
    }

    TrackShareEntity share = track.getTrackShare();
    if (share == null) {
      throw new NotFoundException("Track is not published");
    }

    detachShareFromAllUsers(share);
    track.setTrackShare(null);

    removeTrackFromAllGroups(trackId);
    boardRepository.clearSelectedTrackFromAllBoards(trackId);

    log.info("Track {} unpublished", trackId);
  }


  private void removeTrackFromAllGroups(Long trackId) {
    List<GroupEntity> groups = groupRepository.findAllContainingTrack(trackId);
    for (GroupEntity group : groups) {
      group.getTracks().removeIf(t -> t.getId().equals(trackId));
    }
  }

  private void removeTrackFromGroupsOwnedByUser(Long trackId, Long ownerId) {
    List<GroupEntity> groups = groupRepository.findAllContainingTrackOwnedByUser(trackId, ownerId);
    for (GroupEntity group : groups) {
      group.getTracks().removeIf(t -> t.getId().equals(trackId));
    }
  }

  private void detachShareFromAllUsers(TrackShareEntity share) {
    for (UserEntity user : new HashSet<>(share.getUsers())) {
      user.getShares().remove(share);
      share.getUsers().remove(user);
    }
  }

  @Transactional(readOnly = true)
  public List<Track> getSubscribedTracks() {
    Long userId = SecurityUtils.getCurrentUserId();
    UserEntity user = userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException("User not found with id: " + userId));

    return user.getShares().stream()
            .map(TrackShareEntity::getTrack)
            .map(entity -> trackMapper.toDto(entity, userId))
            .toList();
  }

  @Transactional(readOnly = true)
  public List<Track> getPublishedTracks() {
    Set<TrackShareEntity> allShares = new HashSet<>(trackShareRepository.findAll());

    return allShares.stream()
            .map(TrackShareEntity::getTrack)
            .map(entity -> trackMapper.toDto(entity, SecurityUtils.getCurrentUserId()))
            .toList();

  }

  @Transactional(readOnly = true)
  public List<Track> getTracksPublishedByCurrentUser() {
    Long userId = SecurityUtils.getCurrentUserId();

    Set<TrackShareEntity> allShares = new HashSet<>(trackShareRepository.findAll());

    return allShares.stream()
            .map(TrackShareEntity::getTrack)
            .filter(track -> track.getOwner().getId().equals(userId))
            .map(entity -> trackMapper.toDto(entity, userId))
            .toList();

  }

  private UUID generateUniqueShareCode() {
    UUID shareCode;
    do {
      shareCode = UUID.randomUUID();
    } while (trackShareRepository.existsByShareCode(shareCode.toString()));
    return shareCode;
  }

}

