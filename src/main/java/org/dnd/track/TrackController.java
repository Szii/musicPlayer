package org.dnd.track;

import com.giffing.bucket4j.spring.boot.starter.context.RateLimiting;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.dnd.api.MusicTracksApi;
import org.dnd.api.model.*;
import org.dnd.playback.PlaybackService;
import org.dnd.track.trackShare.ShareService;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static org.dnd.configuration.limiting.RateLimitNames.*;

@RequestMapping("/api/v1")
@Tag(name = "Tracks", description = "Music track management endpoints")
@RestController
@AllArgsConstructor
@Validated
public class TrackController implements MusicTracksApi {

  private final TrackService trackService;

  private final ShareService shareService;

  private final TrackWindowService trackWindowService;

  private final PlaybackService playbackService;

  @Override
  @RateLimiting(
          name = DEFAULT_API,
          cacheKey = CURRENT_USER_KEY,
          ratePerMethod = true
  )
  public ResponseEntity<List<Track>> getUserTracks() throws Exception {
    return ResponseEntity.ok()
            .body(trackService.getAllTracksForUser());
  }

  @Override
  @RateLimiting(
          name = CREATE_API,
          cacheKey = CURRENT_USER_KEY,
          ratePerMethod = true
  )
  public ResponseEntity<Track> createTrack(TrackRequest trackRequest) throws Exception {
    return ResponseEntity.ok().body(trackService.addTrack(trackRequest));
  }

  @Override
  @RateLimiting(
          name = CREATE_API,
          cacheKey = CURRENT_USER_KEY,
          ratePerMethod = true
  )
  public ResponseEntity<Track> createTrackV2(CreateTrackRequestV2 createTrackRequestV2) throws Exception {
    return null;
  }

  @Override
  @RateLimiting(
          name = DEFAULT_API,
          cacheKey = CURRENT_USER_KEY,
          ratePerMethod = true
  )
  public ResponseEntity<Void> deleteTrack(Long trackId) throws Exception {
    trackService.deleteTrack(trackId);
    return ResponseEntity.noContent().build();
  }

  @Override
  @RateLimiting(
          name = DEFAULT_API,
          cacheKey = CURRENT_USER_KEY,
          ratePerMethod = true
  )
  public ResponseEntity<Track> updateTrack(Long trackId, TrackRequest trackRequest) throws Exception {
    return ResponseEntity.ok().body(trackService.updateTrack(trackId, trackRequest));
  }

  @Override
  @RateLimiting(
          name = DEFAULT_API,
          cacheKey = CURRENT_USER_KEY,
          ratePerMethod = true
  )
  public ResponseEntity<Track> updateTrackWindow(Long trackId, Long pointId, TrackWindowRequest trackRequest) throws Exception {
    return ResponseEntity.ok().body(trackWindowService.updateTrackWindow(trackId, pointId, trackRequest));
  }

  @Override
  @RateLimiting(
          name = DEFAULT_API,
          cacheKey = CURRENT_USER_KEY,
          ratePerMethod = true
  )
  public ResponseEntity<Track> deleteTrackWindow(Long trackId, Long pointId) throws Exception {
    return ResponseEntity.ok().body(trackWindowService.deleteTrackWindow(trackId, pointId));
  }

  @Override
  @RateLimiting(
          name = DEFAULT_API,
          cacheKey = CURRENT_USER_KEY,
          ratePerMethod = true
  )
  public ResponseEntity<List<Track>> getPublishedTracks() throws Exception {
    return ResponseEntity.ok().body(shareService.getPublishedTracks());
  }

  @Override
  @RateLimiting(
          name = DEFAULT_API,
          cacheKey = CURRENT_USER_KEY,
          ratePerMethod = true
  )
  public ResponseEntity<List<Track>> getUserSubscribedTracks() throws Exception {
    return ResponseEntity.ok().body(shareService.getSubscribedTracks());
  }

  @Override
  @RateLimiting(
          name = DEFAULT_API,
          cacheKey = CURRENT_USER_KEY,
          ratePerMethod = true
  )
  public ResponseEntity<Track> createTrackWindow(Long trackId, TrackWindowRequest trackRequest) throws Exception {
    return ResponseEntity.ok().body(trackWindowService.createTrackWindow(trackId, trackRequest));
  }

  @Override
  @RateLimiting(
          name = DEFAULT_API,
          cacheKey = CURRENT_USER_KEY,
          ratePerMethod = true
  )
  public ResponseEntity<WaveformResponse> getTrackWaveform(Long trackId) {
    return ResponseEntity.ok(playbackService.getTrackWaveform(trackId));
  }
}


