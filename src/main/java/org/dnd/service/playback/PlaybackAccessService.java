package org.dnd.service.playback;

import lombok.RequiredArgsConstructor;
import org.dnd.api.model.PlayRequest;
import org.dnd.model.BoardEntity;
import org.dnd.model.TrackEntity;
import org.dnd.model.UserEntity;
import org.dnd.repository.BoardRepository;
import org.dnd.repository.TrackRepository;
import org.dnd.repository.TrackWindowRepository;
import org.dnd.repository.UserRepository;
import org.dnd.utils.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlaybackAccessService {

  private final BoardRepository boardRepository;
  private final TrackRepository trackRepository;
  private final TrackWindowRepository trackWindowRepository;
  private final UserRepository userRepository;

  public void requireOwnedBoard(long boardId) {
    BoardEntity board = boardRepository.findById(boardId)
            .orElseThrow(() -> notFound("Board not found"));

    long userId = SecurityUtils.getCurrentUserId();
    if (!board.getOwner().getId().equals(userId)) {
      throw forbidden("Board not owned by user");
    }
  }

  public void requireOwnedBoardByUserId(long boardId, long userId) {
    BoardEntity board = boardRepository.findById(boardId)
            .orElseThrow(() -> notFound("Board not found"));

    if (!board.getOwner().getId().equals(userId)) {
      throw forbidden("Board not owned by user");
    }
  }

  public void requireOwnedTrackByUserId(long trackId, long userId) {
    TrackEntity track = trackRepository.findById(trackId)
            .orElseThrow(() -> notFound("Track not found"));

    if (!track.getOwner().getId().equals(userId)) {
      throw forbidden("Track not owned by user");
    }
  }

  public TrackMetadata getOwnedTrackMetadata(long trackId) {
    long userId = SecurityUtils.getCurrentUserId();

    TrackEntity track = trackRepository.findById(trackId)
            .orElseThrow(() -> notFound("Track not found"));

    if (!track.getOwner().getId().equals(userId)) {
      throw forbidden("Track not owned by user");
    }

    return new TrackMetadata(
            userId,
            track.getId(),
            track.getTrackLink(),
            track.getDuration()
    );
  }

  public BoardPlayData getBoardPlayData(long boardId, PlayRequest request) {
    long userId = SecurityUtils.getCurrentUserId();

    BoardEntity board = boardRepository.findById(boardId)
            .orElseThrow(() -> notFound("Board not found"));

    if (!board.getOwner().getId().equals(userId)) {
      throw forbidden("Board not owned by user");
    }

    TrackEntity track = board.getSelectedTrack();
    if (track == null) {
      throw conflict("No track selected on board");
    }

    if (!hasTrackAccess(track, userId, true)) {
      throw forbidden("Track not accessible");
    }

    TrackWindowRange range = resolveWindow(request != null ? request.getWindowId() : null);

    return new BoardPlayData(
            boardId,
            track.getId(),
            track.getTrackLink(),
            track.getDuration(),
            range.startS(),
            range.endS()
    );
  }

  public TrackPlayData getTrackPlayData(long trackId, PlayRequest request) {
    TrackMetadata metadata = getOwnedTrackMetadata(trackId);
    TrackWindowRange range = resolveWindow(request != null ? request.getWindowId() : null);

    return new TrackPlayData(
            metadata.userId(),
            metadata.trackId(),
            metadata.trackLink(),
            metadata.trackDuration(),
            range.startS(),
            range.endS()
    );
  }

  private TrackWindowRange resolveWindow(Long windowId) {
    if (windowId == null) {
      return TrackWindowRange.EMPTY;
    }

    var window = trackWindowRepository.findById(windowId)
            .orElseThrow(() -> notFound("Track window not found"));

    return new TrackWindowRange(window.getPositionFrom(), window.getPositionTo());
  }

  private boolean hasTrackAccess(TrackEntity track, long userId, boolean allowSharedTrack) {
    if (track.getOwner().getId().equals(userId)) {
      return true;
    }

    if (!allowSharedTrack) {
      return false;
    }

    UserEntity user = userRepository.findById(userId)
            .orElseThrow(() -> notFound("User not found"));

    return track.getTrackShare() != null && track.getTrackShare().getUsers().contains(user);
  }

  private static ResponseStatusException conflict(String message) {
    return new ResponseStatusException(CONFLICT, message);
  }

  private static ResponseStatusException forbidden(String message) {
    return new ResponseStatusException(FORBIDDEN, message);
  }

  private static ResponseStatusException notFound(String message) {
    return new ResponseStatusException(NOT_FOUND, message);
  }

  public record TrackMetadata(
          long userId,
          long trackId,
          String trackLink,
          int trackDuration
  ) {
  }

  public record BoardPlayData(
          long boardId,
          long trackId,
          String trackLink,
          int trackDuration,
          Long windowStartS,
          Long windowEndS
  ) {
  }

  public record TrackPlayData(
          long userId,
          long trackId,
          String trackLink,
          int trackDuration,
          Long windowStartS,
          Long windowEndS
  ) {
  }

  public record TrackWindowRange(Long startS, Long endS) {
    static final TrackWindowRange EMPTY = new TrackWindowRange(null, null);
  }
}