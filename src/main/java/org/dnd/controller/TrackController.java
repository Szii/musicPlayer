package org.dnd.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.dnd.api.MusicTracksApi;
import org.dnd.api.model.*;
import org.dnd.service.TrackPointService;
import org.dnd.service.TrackService;
import org.dnd.service.TrackShareService;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RequestMapping("/api/v1")
@Tag(name = "Tracks", description = "Music track management endpoints")
@RestController
@AllArgsConstructor
@Validated
public class TrackController implements MusicTracksApi {

    private final TrackService trackService;

    private final TrackShareService trackShareService;

    private final TrackPointService trackPointService;

    @Override
    public ResponseEntity<List<Track>> getUserTracks(Long userId) throws Exception {
        return ResponseEntity.ok()
                .body(trackService.getAllTracksForUser(userId));
    }

    public ResponseEntity<Track> createTrack(TrackRequest trackRequest) throws Exception {
        return ResponseEntity.ok().body(trackService.addTrack(trackRequest));
    }

    @Override
    public ResponseEntity<Void> deleteTrack(Long trackId) throws Exception {
        trackService.deleteTrack(trackId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Track> updateTrack(Long trackId, TrackRequest trackRequest) throws Exception {

        return ResponseEntity.ok().body(trackService.updateTrack(trackId, trackRequest));
    }

    @Override
    public ResponseEntity<Track> updateTrackPoint(Long trackId, Long pointId, TrackPointRequest trackRequest) throws Exception {
        return ResponseEntity.ok().body(trackPointService.updateTrackPoint(trackId, pointId, trackRequest));
    }

    @Override
    public ResponseEntity<Track> deleteTrackPoint(Long trackId, Long pointId) throws Exception {
        trackPointService.deleteTrackPoint(trackId, pointId);
        return ResponseEntity.ok().build();
    }

    @Override
    public ResponseEntity<Track> createTrackPoint(Long trackId, TrackPointRequest trackRequest) throws Exception {
        return ResponseEntity.ok().body(trackPointService.createTrackPoint(trackId, trackRequest));
    }

    @Override
    public ResponseEntity<List<TrackShare>> listTrackShares(Long trackId) throws Exception {
        return ResponseEntity.ok().body(trackShareService.getTrackShares(trackId));
    }

    @Override
    public ResponseEntity<TrackShare> shareTrack(Long trackId, TrackShareRequest trackShareRequest) throws Exception {
        trackShareService.shareTrack(trackId, trackShareRequest.getUserId());
        return ResponseEntity.ok().build();
    }

    @Override
    public ResponseEntity<Void> unshareTrack(Long trackId, Long userId) throws Exception {
        trackShareService.unshareTrack(trackId, userId);
        return ResponseEntity.ok().build();
    }

}


