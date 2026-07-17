package org.dnd.track;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dnd.api.model.ReorderTrackWindowsRequest;
import org.dnd.api.model.Track;
import org.dnd.api.model.TrackWindow;
import org.dnd.api.model.TrackWindowRequest;
import org.dnd.exception.BadRequestException;
import org.dnd.exception.ForbiddenException;
import org.dnd.exception.LimitReachedException;
import org.dnd.exception.NotFoundException;
import org.dnd.user.rank.UserRankEvaluatorService;
import org.dnd.utils.SecurityUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@AllArgsConstructor
@Service
@Slf4j
public class TrackWindowService {

  private final TrackRepository trackRepository;
  private final TrackWindowMapper trackWindowMapper;
  private final TrackWindowRepository trackWindowRepository;
  private final TrackMapper trackMapper;
  private final UserRankEvaluatorService userRankEvaluatorService;
  private final SecurityUtils securityUtils;
  private final EntityManager entityManager;

  @Transactional
  public Track deleteTrackWindow(UUID trackId, UUID windowId) {
    log.debug("Deleting track point {} from track {}", windowId, trackId);

    UUID userId = securityUtils.getCurrentUserId();

    TrackEntity track = trackRepository.findById(trackId)
            .orElseThrow(() -> new NotFoundException(
                    "Track with id %s not found" .formatted(trackId)
            ));

    if (!track.getOwner().getId().equals(userId)) {
      throw new ForbiddenException("You can only delete track points from your own tracks");
    }

    TrackWindowEntity point = trackWindowRepository.findByIdAndTrack_Id(windowId, trackId)
            .orElseThrow(() -> new NotFoundException(
                    "Track point with id %s not found for track %s" .formatted(windowId, trackId)
            ));

    trackWindowRepository.clearSelectedWindowFromAllBoards(point.getId());
    trackWindowRepository.deleteById(windowId);
    trackWindowRepository.flush();

    TrackEntity freshTrack = trackRepository.findById(trackId)
            .orElseThrow(() -> new NotFoundException(
                    "Track with id %s not found" .formatted(trackId)
            ));

    normalizeWindowPositions(freshTrack);

    return reloadTrackDto(trackId, userId);
  }

  @Transactional
  public Track updateTrackWindow(
          UUID trackId,
          UUID windowId,
          TrackWindowRequest trackWindowRequest
  ) {
    UUID userId = securityUtils.getCurrentUserId();

    TrackEntity track = trackRepository.findById(trackId)
            .orElseThrow(() -> new NotFoundException(
                    "Track with id %s not found" .formatted(trackId)
            ));

    if (!track.getOwner().getId().equals(userId)) {
      throw new ForbiddenException("You can only update track points on your own tracks");
    }

    TrackWindowEntity entity = trackWindowRepository.findByIdAndTrack_Id(windowId, trackId)
            .orElseThrow(() -> new NotFoundException(
                    "Track point with id %s not found" .formatted(windowId)
            ));

    validateWindowRange(track, trackWindowRequest);

    int originalPosition = entity.getPositionWithinTrack();

    entity = trackWindowMapper.updateFromRequest(trackWindowRequest, entity);

    // Normal window update must not reorder windows.
    // Reordering is handled by reorderTrackWindows(...).
    entity.setPositionWithinTrack(originalPosition);

    trackWindowRepository.save(entity);

    log.debug("Updated track point with id {} for track {}", windowId, trackId);

    return reloadTrackDto(trackId, userId);
  }

  @Transactional
  public Track reorderTrackWindows(UUID trackId, ReorderTrackWindowsRequest request) {
    UUID userId = securityUtils.getCurrentUserId();

    TrackEntity track = trackRepository.findById(trackId)
            .orElseThrow(() -> new NotFoundException(
                    "Track with id %s not found" .formatted(trackId)
            ));

    if (!track.getOwner().getId().equals(userId)) {
      throw new ForbiddenException("You can only reorder windows on your own tracks");
    }

    List<UUID> requestedWindowIds = request.getWindowIds();

    if (requestedWindowIds == null || requestedWindowIds.isEmpty()) {
      throw new BadRequestException("Window ids must not be empty");
    }

    if (requestedWindowIds.size() != requestedWindowIds.stream().distinct().count()) {
      throw new BadRequestException("Window ids must not contain duplicates");
    }

    List<TrackWindowEntity> currentWindows = sortedWindows(track);

    if (requestedWindowIds.size() != currentWindows.size()) {
      throw new BadRequestException("Request must contain all windows of the track");
    }

    var windowsById = currentWindows.stream()
            .collect(java.util.stream.Collectors.toMap(
                    TrackWindowEntity::getId,
                    window -> window
            ));

    List<TrackWindowEntity> orderedWindows = new ArrayList<>();

    for (UUID windowId : requestedWindowIds) {
      TrackWindowEntity window = windowsById.get(windowId);

      if (window == null) {
        throw new BadRequestException(
                "Window with id %s does not belong to track %s" .formatted(windowId, trackId)
        );
      }

      orderedWindows.add(window);
    }

    rewritePositionsSafely(orderedWindows);

    return reloadTrackDto(trackId, userId);
  }

  @Transactional
  public Track createTrackWindow(
          UUID trackId,
          TrackWindowRequest trackWindowRequest
  ) throws BadRequestException {
    UUID userId = securityUtils.getCurrentUserId();

    TrackEntity track = trackRepository.findById(trackId)
            .orElseThrow(() -> new NotFoundException(
                    "Track with id %s not found" .formatted(trackId)
            ));

    if (!track.getOwner().getId().equals(userId)) {
      throw new ForbiddenException("You can only create track windows on your own tracks");
    }

    if (!userRankEvaluatorService.canCreateTrackWindowForTrack(track.getOwner(), track)) {
      throw new LimitReachedException("Track window limit reached");
    }

    validateWindowRange(track, trackWindowRequest);

    TrackWindowEntity window = trackWindowMapper.fromRequest(trackWindowRequest, track);
    window.setTrack(track);
    window.setPositionWithinTrack(nextWindowPosition(track));

    track.getTrackWindows().add(window);
    trackWindowRepository.saveAndFlush(window);

    log.debug("Created track point with id {} for track {}", window.getId(), trackId);

    return reloadTrackDto(trackId, userId);
  }

  @Transactional
  public TrackWindow getTrackWindow(UUID trackId, UUID windowId) {
    TrackEntity track = trackRepository.findById(trackId)
            .orElseThrow(() -> new NotFoundException(
                    "Track with id %s not found" .formatted(trackId)
            ));

    if (!track.getOwner().getId().equals(securityUtils.getCurrentUserId())) {
      throw new ForbiddenException("You can only get track points for your own tracks");
    }

    TrackWindowEntity entity = trackWindowRepository.findByIdAndTrack_Id(windowId, trackId)
            .orElseThrow(() -> new NotFoundException(
                    "Track point with id %s not found" .formatted(windowId)
            ));

    log.debug("Retrieved track point with id {} for track {}", entity.getId(), trackId);
    return trackWindowMapper.toDto(entity);
  }

  private void validateWindowRange(TrackEntity track, TrackWindowRequest request) {
    if (request.getPositionFrom() < 0 || request.getPositionTo() > track.getDuration()) {
      throw new BadRequestException("Track window position must be within track duration");
    }

    if (request.getPositionFrom() > request.getPositionTo()) {
      throw new BadRequestException(
              "Track window from position must not be greater than track window to position"
      );
    }
  }

  private int nextWindowPosition(TrackEntity track) {
    return track.getTrackWindows().stream()
            .mapToInt(TrackWindowEntity::getPositionWithinTrack)
            .max()
            .orElse(0) + 1;
  }

  private void normalizeWindowPositions(TrackEntity track) {
    List<TrackWindowEntity> windows = sortedWindows(track);
    rewritePositionsSafely(windows);
  }

  private List<TrackWindowEntity> sortedWindows(TrackEntity track) {
    return track.getTrackWindows().stream()
            .sorted(
                    Comparator
                            .comparingInt(TrackWindowEntity::getPositionWithinTrack)
                            .thenComparing(
                                    TrackWindowEntity::getPositionFrom,
                                    Comparator.nullsLast(Long::compareTo)
                            )
                            .thenComparing(
                                    TrackWindowEntity::getId,
                                    Comparator.nullsLast(UUID::compareTo)
                            )
            )
            .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
  }

  /**
   * Two-step rewrite avoids unique constraint conflicts.
   * <p>
   * Example:
   * A = 1, B = 2
   * <p>
   * Moving B to position 1 cannot directly set B = 1 while A is still 1.
   * So first all positions are moved to temporary negative values,
   * then final 1-based positions are written.
   */
  private void rewritePositionsSafely(List<TrackWindowEntity> windows) {
    if (windows.isEmpty()) {
      return;
    }

    for (int i = 0; i < windows.size(); i++) {
      windows.get(i).setPositionWithinTrack(-(i + 1));
    }

    trackWindowRepository.flush();

    for (int i = 0; i < windows.size(); i++) {
      windows.get(i).setPositionWithinTrack(i + 1);
    }

    trackWindowRepository.flush();
  }

  private Track reloadTrackDto(UUID trackId, UUID userId) {
    trackWindowRepository.flush();
    entityManager.clear();

    TrackEntity freshTrack = trackRepository.findById(trackId)
            .orElseThrow(() -> new NotFoundException(
                    "Track with id %s not found" .formatted(trackId)
            ));

    return trackMapper.toDto(freshTrack, userId);
  }
}