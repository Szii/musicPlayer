package org.dnd.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.dnd.api.MusicTracksApi;
import org.dnd.api.model.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RequestMapping("/api/v1")
@Tag(name = "Tracks", description = "Music track management endpoints")
@RestController
@Validated
public class TrackController implements MusicTracksApi {

    @Override
    public ResponseEntity<List<Track>> getUserTracks(Long userId) throws Exception {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }

    @Override
    public ResponseEntity<Track> createTrack(TrackRequest trackRequest) throws Exception {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }

    @Override
    public ResponseEntity<Void> deleteTrack(Long trackId) throws Exception {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }

    @Override
    public ResponseEntity<Track> updateTrack(Long trackId, TrackRequest trackRequest) throws Exception {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }

    @Override
    public ResponseEntity<Track> updateTrackPoint(Long trackId, Long pointId, TrackPointRequest trackRequest) throws Exception {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }

    @Override
    public ResponseEntity<Track> deleteTrackPoint(Long trackId, Long pointId) throws Exception {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }

    @Override
    public ResponseEntity<Track> createTrackPoint(Long trackId, TrackPointRequest trackRequest) throws Exception {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }

    @Override
    public ResponseEntity<List<TrackShare>> listTrackShares(Long trackId) throws Exception {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }

    @Override
    public ResponseEntity<TrackShare> shareTrack(Long trackId, TrackShareRequest trackShareRequest) throws Exception {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }

    @Override
    public ResponseEntity<Void> unshareTrack(Long trackId, Long userId) throws Exception {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }

}


