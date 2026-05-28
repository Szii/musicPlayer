package org.dnd.playback;

import com.giffing.bucket4j.spring.boot.starter.context.RateLimiting;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.dnd.api.PlaybackApi;
import org.dnd.api.model.PlayRequest;
import org.dnd.api.model.PlaybackState;
import org.dnd.api.model.SeekRequest;
import org.dnd.api.model.StreamInfoResponse;
import org.dnd.security.JwtService;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.dnd.configuration.limiting.RateLimitNames.*;

@RequestMapping("/api/v1")
@Tag(name = "Playback", description = "Playback control operations for a specific board")
@RestController
@AllArgsConstructor
@Validated

public class PlaybackController implements PlaybackApi {

  private final PlaybackService playbackService;
  private final JwtService jwtService;

  @Override
  @RateLimiting(
          name = DEFAULT_API,
          cacheKey = CURRENT_USER_KEY,
          ratePerMethod = true
  )
  public ResponseEntity<PlaybackState> getBoardPlaybackState(Long boardId) {
    return ResponseEntity.ok(playbackService.getState(boardId));
  }

  @Override
  @RateLimiting(
          name = DEFAULT_API,
          cacheKey = CURRENT_USER_KEY,
          ratePerMethod = true
  )
  public ResponseEntity<PlaybackState> playBoard(Long boardId, PlayRequest playRequest) {
    return ResponseEntity.ok(playbackService.playBoard(boardId, playRequest));
  }

  @Override
  @RateLimiting(
          name = DEFAULT_API,
          cacheKey = CURRENT_USER_KEY,
          ratePerMethod = true
  )
  public ResponseEntity<PlaybackState> stopBoard(Long boardId) {
    return ResponseEntity.ok(playbackService.stop(boardId));
  }

  @Override
  @RateLimiting(
          name = DEFAULT_API,
          cacheKey = CURRENT_USER_KEY,
          ratePerMethod = true
  )
  @Deprecated
  public ResponseEntity<PlaybackState> pauseBoard(Long boardId) {
    return ResponseEntity.ok(playbackService.pause(boardId));
  }

  @Override
  @RateLimiting(
          name = DEFAULT_API,
          cacheKey = CURRENT_USER_KEY,
          ratePerMethod = true
  )
  @Deprecated
  public ResponseEntity<PlaybackState> resumeBoard(Long boardId) {
    return ResponseEntity.ok(playbackService.resume(boardId));
  }

  @Override
  @RateLimiting(
          name = DEFAULT_API,
          cacheKey = CURRENT_USER_KEY,
          ratePerMethod = true
  )
  @Deprecated
  public ResponseEntity<PlaybackState> seekBoard(Long boardId, SeekRequest seekRequest) {
    return ResponseEntity.ok(playbackService.seek(boardId, seekRequest));
  }

  @Override
  @RateLimiting(
          name = STREAM_API,
          cacheKey = STREAM_TOKEN_KEY,
          ratePerMethod = true
  )
  public ResponseEntity<Resource> streamBoardAudio(Long boardId, String streamToken) {
    jwtService.validateStreamTokenOrThrow(streamToken, boardId);
    long userId = Long.parseLong(jwtService.getUserIdFromToken(streamToken));
    return playbackService.streamMp3ForUser(boardId, userId);
  }

  @Override
  @RateLimiting(
          name = DEFAULT_API,
          cacheKey = CURRENT_USER_KEY,
          ratePerMethod = true
  )
  public ResponseEntity<PlaybackState> playTrack(Long trackId, PlayRequest playRequest) {
    return ResponseEntity.ok(playbackService.playTrack(trackId, playRequest));
  }

  @Override
  @RateLimiting(
          name = STREAM_API,
          cacheKey = STREAM_TOKEN_KEY,
          ratePerMethod = true
  )
  public ResponseEntity<Resource> streamTrackAudio(Long trackId, String streamToken) {
    jwtService.validateTrackStreamTokenOrThrow(streamToken, trackId);
    long userId = Long.parseLong(jwtService.getUserIdFromToken(streamToken));
    return playbackService.streamMp3ForTrack(trackId, userId);
  }

  @Override
  @RateLimiting(
          name = DEFAULT_API,
          cacheKey = CURRENT_USER_KEY,
          ratePerMethod = true
  )
  public ResponseEntity<StreamInfoResponse> getTrackStreamInfo(Long trackId) {
    return ResponseEntity.ok(playbackService.getTrackStreamInfo(trackId));
  }
}