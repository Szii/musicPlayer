package org.dnd.service.playback;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dnd.api.model.*;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;

import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlaybackService {

  private final PlaybackAccessService accessService;
  private final StreamSessionsManager sessionsManager;

  public PlaybackState getState(long boardId) {
    accessService.requireOwnedBoard(boardId);

    return sessionsManager.getBoardSession(boardId)
            .map(StreamSession::snapshot)
            .orElse(stoppedBoardState(boardId));
  }

  public PlaybackState playBoard(long boardId, PlayRequest request) {
    PlaybackAccessService.BoardPlayData playData = accessService.getBoardPlayData(boardId, request);

    StreamSession session = sessionsManager.startBoardSession(
            playData.boardId(),
            playData.trackId(),
            playData.trackLink(),
            playData.trackDuration(),
            playData.windowStartS(),
            playData.windowEndS()
    );

    return session.snapshot();
  }

  public PlaybackState stop(long boardId) {
    accessService.requireOwnedBoard(boardId);
    sessionsManager.stopBoardSession(boardId);
    return stoppedBoardState(boardId);
  }

  @Deprecated
  public PlaybackState pause(long boardId) {
    accessService.requireOwnedBoard(boardId);

    return sessionsManager.getBoardSession(boardId)
            .map(StreamSession::snapshot)
            .orElse(stoppedBoardState(boardId));
  }

  @Deprecated
  public PlaybackState resume(long boardId) {
    accessService.requireOwnedBoard(boardId);

    return sessionsManager.getBoardSession(boardId)
            .map(StreamSession::snapshot)
            .orElse(stoppedBoardState(boardId));
  }

  @Deprecated
  public PlaybackState seek(long boardId, SeekRequest request) {
    accessService.requireOwnedBoard(boardId);

    return sessionsManager.getBoardSession(boardId)
            .map(StreamSession::snapshot)
            .orElse(stoppedBoardState(boardId));
  }

  public ResponseEntity<Resource> streamMp3ForUser(long boardId, long userId) {
    accessService.requireOwnedBoardByUserId(boardId, userId);

    StreamSession session = sessionsManager.getBoardSession(boardId)
            .filter(StreamSession::canServeStream)
            .orElseThrow(() -> conflict("Board is not playing"));

    try {
      return session.buildStreamResponse();
    } catch (IOException e) {
      log.error("[board={}] Failed to start stream", boardId, e);
      throw new ResponseStatusException(INTERNAL_SERVER_ERROR, "Streaming failed");
    }
  }

  public PlaybackState playTrack(long trackId, PlayRequest request) {
    PlaybackAccessService.TrackPlayData playData = accessService.getTrackPlayData(trackId, request);

    StreamSession session = sessionsManager.startTrackSession(
            playData.userId(),
            playData.trackId(),
            playData.trackLink(),
            playData.trackDuration(),
            playData.windowStartS(),
            playData.windowEndS()
    );

    return session.snapshot();
  }

  public ResponseEntity<Resource> streamMp3ForTrack(long trackId, long userId) {
    accessService.requireOwnedTrackByUserId(trackId, userId);

    StreamSession session = sessionsManager.getTrackSession(userId, trackId)
            .filter(StreamSession::canServeStream)
            .orElseThrow(() -> conflict("Track is not playing"));

    try {
      return session.buildStreamResponse();
    } catch (IOException e) {
      log.error("[track={}] Failed to start stream", trackId, e);
      throw new ResponseStatusException(INTERNAL_SERVER_ERROR, "Streaming failed");
    }
  }

  public StreamInfoResponse getTrackStreamInfo(Long trackId) {
    PlaybackAccessService.TrackMetadata metadata = accessService.getOwnedTrackMetadata(trackId);
    StreamSession session = sessionsManager.getTrackSession(metadata.userId(), metadata.trackId()).orElse(null);

    StreamInfoResponse info = new StreamInfoResponse();
    info.setTrackId(trackId);
    info.setMimeType("audio/mpeg");
    info.setRangeSupported(true);

    if (session != null && session.getDurationMs() > 0) {
      info.setDurationS(session.getDurationMs() / 1000L);
    } else if (metadata.trackDuration() > 0) {
      info.setDurationS((long) metadata.trackDuration());
    }

    return info;
  }

  public WaveformResponse getTrackWaveform(Long trackId) {
    PlaybackAccessService.TrackMetadata metadata = accessService.getOwnedTrackMetadata(trackId);

    WaveformSession session = sessionsManager.getOrCreateWaveformSession(
            metadata.userId(),
            metadata.trackId(),
            metadata.trackLink(),
            metadata.trackDuration()
    );

    return session.getWaveformResponse();
  }

  private static PlaybackState stoppedBoardState(long boardId) {
    PlaybackState state = new PlaybackState();
    state.setBoardId(boardId);
    state.setStatus(PlaybackStatus.STOPPED);
    return state;
  }

  private static ResponseStatusException conflict(String message) {
    return new ResponseStatusException(CONFLICT, message);
  }
}