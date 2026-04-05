package org.dnd.service.playback;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dnd.api.model.*;
import org.dnd.exception.NotFoundException;
import org.dnd.model.BoardEntity;
import org.dnd.model.TrackEntity;
import org.dnd.model.UserEntity;
import org.dnd.repository.BoardRepository;
import org.dnd.repository.TrackRepository;
import org.dnd.repository.TrackWindowRepository;
import org.dnd.repository.UserRepository;
import org.dnd.utils.SecurityUtils;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;

import static org.springframework.http.HttpStatus.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlaybackService {

  private final BoardRepository boardRepository;
  private final TrackWindowRepository trackWindowRepository;
  private final UserRepository userRepository;
  private final TrackRepository trackRepository;
  private final StreamSessionsManager sessionsManager;

  @Transactional(readOnly = true)
  public PlaybackState getState(long boardId) {
    requireOwnedBoard(boardId);
    return sessionsManager.getBoardSession(boardId)
            .map(StreamSession::snapshot)
            .orElse(stoppedBoardState(boardId));
  }

  /**
   * Validate against DB and then unleash the stream separately after all validations
   * are complete to not starve the hikari pool with long running streaming operations
   */
  @Transactional(readOnly = true)
  public PlaybackState playBoard(long boardId, PlayRequest request) {
    BoardEntity board = requireOwnedBoard(boardId);

    TrackEntity track = board.getSelectedTrack();
    if (track == null) throw conflict("No track selected on board");
    if (!validateTrackAccessForCurrentUser(track, true)) throw forbidden("Track not accessible");

    Long windowId = request != null ? request.getWindowId() : null;
    Long windowStartS = null;
    Long windowEndS = null;
    if (windowId != null) {
      var window = trackWindowRepository.findById(windowId)
              .orElseThrow(() -> notFound("Track window not found"));
      windowStartS = window.getPositionFrom();
      windowEndS = window.getPositionTo();
    }

    StreamSession session = sessionsManager.startBoardSession(
            boardId,
            track.getId(),
            track.getTrackLink(),
            track.getDuration(),
            windowStartS,
            windowEndS
    );

    return session.snapshot();
  }

  @Transactional(readOnly = true)
  public PlaybackState stop(long boardId) {
    requireOwnedBoard(boardId);
    sessionsManager.stopBoardSession(boardId);
    return stoppedBoardState(boardId);
  }

  @Deprecated
  @Transactional(readOnly = true)
  public PlaybackState pause(long boardId) {
    requireOwnedBoard(boardId);
    return sessionsManager.getBoardSession(boardId)
            .map(StreamSession::snapshot)
            .orElse(stoppedBoardState(boardId));
  }

  @Deprecated
  @Transactional(readOnly = true)
  public PlaybackState resume(long boardId) {
    requireOwnedBoard(boardId);
    return sessionsManager.getBoardSession(boardId)
            .map(StreamSession::snapshot)
            .orElse(stoppedBoardState(boardId));
  }

  @Deprecated
  @Transactional(readOnly = true)
  public PlaybackState seek(long boardId, SeekRequest req) {
    requireOwnedBoard(boardId);
    return sessionsManager.getBoardSession(boardId)
            .map(StreamSession::snapshot)
            .orElse(stoppedBoardState(boardId));
  }

  /**
   * DB validations needs to run first in separate transactional block to not
   * starve the hikari pool
   */
  public ResponseEntity<Resource> streamMp3ForUser(long boardId, long userId) {
    StreamSession s = validateAndGetBoardSession(boardId, userId);
    try {
      return s.buildStreamResponse();
    } catch (IOException e) {
      log.error("[board={}] Failed to start stream", boardId, e);
      throw new ResponseStatusException(INTERNAL_SERVER_ERROR, "Streaming failed");
    }
  }

  @Transactional(readOnly = true)
  private StreamSession validateAndGetBoardSession(long boardId, long userId) {
    requireOwnedBoardByUserId(boardId, userId);

    return sessionsManager.getBoardSession(boardId)
            .filter(StreamSession::canServeStream)
            .orElseThrow(() -> conflict("Board is not playing"));
  }

  @Transactional(readOnly = true)
  public PlaybackState playTrack(long trackId, PlayRequest request) {
    TrackEntity track = requireOwnedTrack(trackId);
    Long userId = SecurityUtils.getCurrentUserId();

    Long windowId = request != null ? request.getWindowId() : null;
    Long windowStartS = null;
    Long windowEndS = null;
    if (windowId != null) {
      var window = trackWindowRepository.findById(windowId)
              .orElseThrow(() -> notFound("Track window not found"));
      windowStartS = window.getPositionFrom();
      windowEndS = window.getPositionTo();
    }

    StreamSession session = sessionsManager.startTrackSession(
            userId,
            trackId,
            track.getTrackLink(),
            track.getDuration(),
            windowStartS,
            windowEndS
    );

    return session.snapshot();
  }

  /**
   * DB validations needs to run first in separate transactional block to not
   * starve the hikari pool
   */
  public ResponseEntity<Resource> streamMp3ForTrack(long trackId, long userId) {
    StreamSession s = validateAndGetTrackSession(trackId, userId);
    try {
      return s.buildStreamResponse();
    } catch (IOException e) {
      log.error("[track={}] Failed to start stream", trackId, e);
      throw new ResponseStatusException(INTERNAL_SERVER_ERROR, "Streaming failed");
    }
  }

  @Transactional(readOnly = true)
  private StreamSession validateAndGetTrackSession(long trackId, long userId) {
    TrackEntity track = trackRepository.findById(trackId)
            .orElseThrow(() -> notFound("Track not found"));
    if (!track.getOwner().getId().equals(userId)) throw forbidden("Track not owned by user");

    return sessionsManager.getTrackSession(userId, trackId)
            .filter(StreamSession::canServeStream)
            .orElseThrow(() -> conflict("Track is not playing"));
  }

  @Transactional(readOnly = true)
  public StreamInfoResponse getTrackStreamInfo(Long trackId) {
    TrackEntity track = requireOwnedTrack(trackId);
    Long userId = SecurityUtils.getCurrentUserId();

    StreamSession s = sessionsManager.getTrackSession(userId, trackId).orElse(null);

    StreamInfoResponse info = new StreamInfoResponse();
    info.setTrackId(trackId);
    info.setMimeType("audio/mpeg");
    info.setRangeSupported(true);

    if (s != null && s.getDurationMs() > 0) {
      info.setDurationS(s.getDurationMs() / 1000L);
    } else if (track.getDuration() != 0) {
      info.setDurationS((long) track.getDuration());
    }

    return info;
  }

  @Transactional(readOnly = true)
  public WaveformResponse getTrackWaveform(Long trackId) {
    TrackEntity track = requireOwnedTrack(trackId);
    Long userId = SecurityUtils.getCurrentUserId();

    StreamSession session = sessionsManager.getOrCreateTrackSessionForWaveform(
            userId,
            track.getId(),
            track.getTrackLink(),
            track.getDuration()
    );
    return session.getWaveformResponse();
  }

  private static PlaybackState stoppedBoardState(long boardId) {
    PlaybackState ps = new PlaybackState();
    ps.setBoardId(boardId);
    ps.setStatus(PlaybackStatus.STOPPED);
    return ps;
  }

  private BoardEntity requireOwnedBoard(long boardId) {
    Long userId = SecurityUtils.getCurrentUserId();
    BoardEntity board = boardRepository.findById(boardId)
            .orElseThrow(() -> notFound("Board not found"));
    if (!board.getOwner().getId().equals(userId)) throw forbidden("Board not owned by user");
    return board;
  }

  private TrackEntity requireOwnedTrack(long trackId) {
    Long userId = SecurityUtils.getCurrentUserId();
    TrackEntity track = trackRepository.findById(trackId)
            .orElseThrow(() -> notFound("Track not found"));
    if (!track.getOwner().getId().equals(userId)) throw forbidden("Track not owned by user");
    return track;
  }

  private void requireOwnedBoardByUserId(long boardId, long userId) {
    BoardEntity board = boardRepository.findById(boardId)
            .orElseThrow(() -> notFound("Board not found"));
    if (!board.getOwner().getId().equals(userId)) throw forbidden("Board not owned by user");
  }

  private static ResponseStatusException conflict(String msg) {
    return new ResponseStatusException(CONFLICT, msg);
  }

  private static ResponseStatusException forbidden(String msg) {
    return new ResponseStatusException(FORBIDDEN, msg);
  }

  private static ResponseStatusException notFound(String msg) {
    return new ResponseStatusException(NOT_FOUND, msg);
  }

  boolean validateTrackAccessForCurrentUser(TrackEntity track, boolean isSharedTrackValid) {
    UserEntity user = userRepository.findById(SecurityUtils.getCurrentUserId())
            .orElseThrow(() -> new NotFoundException(
                    String.format("User with id %d not found", SecurityUtils.getCurrentUserId())));

    if (isSharedTrackValid) {
      return track.getOwner().getId().equals(SecurityUtils.getCurrentUserId()) ||
              (track.getTrackShare() != null && track.getTrackShare().getUsers().contains(user));
    } else {
      return track.getOwner().getId().equals(SecurityUtils.getCurrentUserId());
    }
  }
}