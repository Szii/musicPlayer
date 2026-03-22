package org.dnd.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.dnd.api.ShareApi;
import org.dnd.api.model.PublishTrackRequest;
import org.dnd.api.model.SubscribeRequest;
import org.dnd.api.model.TrackShareResponse;
import org.dnd.service.ShareService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/api/v1")
@Tag(name = "Share", description = "Endpoints for sharing and subscribing to music tracks")
@RestController
@AllArgsConstructor
@Validated
public class ShareController implements ShareApi {

    private final ShareService shareService;

    @Override
    public ResponseEntity<TrackShareResponse> publishTrack(Long trackId, PublishTrackRequest request) {
        TrackShareResponse trackShare = shareService.publish(trackId, request.getDescription());
        return ResponseEntity.status(HttpStatus.CREATED).body(trackShare);
    }

    @Override
    public ResponseEntity<Void> unpublishTrack(Long trackId) {
        shareService.unpublish(trackId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> subscribeToTrack(
            SubscribeRequest request) {
        shareService.subscribe(request.getShareCode());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @Override
    public ResponseEntity<Void> unsubscribeFromTrack(
            Long trackId) {
        shareService.unsubscribe(trackId);
        return ResponseEntity.noContent().build();
    }
}
