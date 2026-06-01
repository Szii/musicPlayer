package org.dnd.track;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dnd.api.model.CreateTrackRequestV2;
import org.dnd.api.model.Track;
import org.dnd.api.model.TrackRequest;
import org.dnd.board.BoardRepository;
import org.dnd.exception.ForbiddenException;
import org.dnd.exception.LimitReachedException;
import org.dnd.exception.NotFoundException;
import org.dnd.group.GroupEntity;
import org.dnd.group.GroupRepository;
import org.dnd.track.trackShare.TrackShareEntity;
import org.dnd.user.UserEntity;
import org.dnd.user.UserRepository;
import org.dnd.user.rank.UserRankEvaluatorService;
import org.dnd.utils.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class TrackService {
  private final TrackRepository trackRepository;
  private final UserRepository userRepository;
  private final TrackMetadataService trackMetadataService;
  private final TrackMapper mapper;
  private final BoardRepository boardRepository;
  private final GroupRepository groupRepository;
  private final UserRankEvaluatorService userRankEvaluatorService;
  private final TrackWindowRepository trackWindowRepository;

  @Transactional
  public void deleteTrack(Long trackId) {
    log.debug("Deleting track with id {}", trackId);

    TrackEntity track = trackRepository.findById(trackId)
            .orElseThrow(() -> new NotFoundException(
                    String.format("Track with id %d not found", trackId)));

    if (!track.getOwner().getId().equals(SecurityUtils.getCurrentUserId())) {
      throw new ForbiddenException("You can only delete tracks you own");
    }

    removeShareCompletely(track);
    removeTrackFromAllGroups(trackId);

    boardRepository.clearSelectedTrackFromAllBoards(trackId);
    trackRepository.delete(track);
  }

  @Transactional
  public Track addTrack(TrackRequest trackRequest) {
    log.debug("Adding track {}", trackRequest);
    Long userId = SecurityUtils.getCurrentUserId();
    UserEntity owner = userRepository.getReferenceById(userId);

    if (!userRankEvaluatorService.canCreateTrack(owner)) {
      throw new LimitReachedException("Track limit reached");
    }

    TrackEntity track = mapper.toEntity(trackRequest);
    setTrackMetadata(track, trackRequest);
    track.setOwner(owner);

    return mapper.toDto(trackRepository.save(track), userId);
  }

  @Transactional
  public Track addTrackV2(CreateTrackRequestV2 trackRequest) {
    log.debug("Adding track {}", trackRequest);
    Long userId = SecurityUtils.getCurrentUserId();
    UserEntity owner = userRepository.getReferenceById(userId);

    if (!userRankEvaluatorService.canCreateTrack(owner)) {
      throw new LimitReachedException("Track limit reached");
    }

    TrackEntity track = mapper.toEntity(trackRequest);
    track.setOwner(owner);

    return mapper.toDto(trackRepository.save(track), userId);
  }


  private void setTrackMetadata(TrackEntity track, TrackRequest trackRequest) {
    TrackMetadata meta = trackMetadataService.resolveMetadata(trackRequest.getTrackLink());
    track.setTrackOriginalName(meta.title());
    if (track.getTrackName() == null || track.getTrackName().isEmpty()) {
      track.setTrackName(meta.title());
    }
    if (meta.durationSecondsOrNull() != null) {
      track.setDuration(meta.durationSecondsOrNull().intValue());
    }
  }

  @Transactional
  public Track updateTrack(Long trackId, TrackRequest request) {
    log.debug("Updating track with id {}", trackId);
    Long userId = SecurityUtils.getCurrentUserId();
    TrackEntity entity = trackRepository.findById(trackId)
            .orElseThrow(() -> new NotFoundException(
                    String.format("Track with id %d not found", trackId)));

    if (!entity.getOwner().getId().equals(userId)) {
      throw new ForbiddenException("You can only update tracks you own");
    }

    boardRepository.clearSelectedWindowForTrack(trackId);

    trackWindowRepository.deleteAllByTrackId(trackId);

    mapper.updateTrackFromRequest(request, entity);
    entity.getTrackWindows().clear();

    TrackEntity saved = trackRepository.save(entity);
    return mapper.toDto(saved, userId);
  }

  @Transactional(readOnly = true)
  public List<Track> getAllTracksForUser() {
    Long userId = SecurityUtils.getCurrentUserId();
    log.debug("Getting tracks for user with id {}", userId);

    return trackRepository.findAccessibleTracksForUser(userId).stream()
            .map(entity -> mapper.toDto(entity, userId))
            .collect(Collectors.toList());
  }

  private void removeTrackFromAllGroups(Long trackId) {
    List<GroupEntity> groups = groupRepository.findAllContainingTrack(trackId);
    for (GroupEntity group : groups) {
      group.getTracks().removeIf(t -> t.getId().equals(trackId));
    }
  }

  private void removeShareCompletely(TrackEntity track) {
    TrackShareEntity share = track.getTrackShare();
    if (share == null) {
      return;
    }

    for (UserEntity user : new HashSet<>(share.getUsers())) {
      user.getShares().remove(share);
      share.getUsers().remove(user);
    }

    track.setTrackShare(null);
    share.setTrack(null);
  }
}