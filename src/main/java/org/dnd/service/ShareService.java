package org.dnd.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dnd.exception.ForbiddenException;
import org.dnd.exception.NotFoundException;
import org.dnd.model.TrackEntity;
import org.dnd.model.TrackShareEntity;
import org.dnd.model.UserEntity;
import org.dnd.repository.TrackRepository;
import org.dnd.repository.TrackShareRepository;
import org.dnd.repository.UserRepository;
import org.dnd.utils.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class ShareService {

    private final TrackShareRepository trackShareRepository;
    private final TrackRepository trackRepository;
    private final UserRepository userRepository;

    @Transactional
    public TrackShareEntity publish(Long trackId, String description) {
        TrackEntity track = trackRepository.findById(trackId)
                .orElseThrow(() -> new NotFoundException("Track not found with id: " + trackId));

        if (!track.getOwner().getId().equals(SecurityUtils.getCurrentUserId())) {
            throw new ForbiddenException("You can only publish tracks you own");
        }
        if (track.getTrackShare() != null) {
            throw new NotFoundException("Track is already published");
        }

        TrackShareEntity share = new TrackShareEntity();
        share.setDescription(description);
        share.setShareCode(UUID.randomUUID().toString());

        track.setTrackShare(share);
        share.setTrack(track);

        return share;
    }


    @Transactional
    public void subscribe(String shareCode) {
        Long userId = SecurityUtils.getCurrentUserId();
        TrackShareEntity trackShare = trackShareRepository.findByShareCode(shareCode)
                .orElseThrow(() -> new NotFoundException("Invalid share code: " + shareCode));

        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found with id: " + userId));

        TrackEntity track = trackShare.getTrack();
        user.getSubscribedTracks().add(track);
        userRepository.save(user);

        log.info("User {} subscribed to track {} via workshop", userId, track.getId());
    }

    @Transactional
    public void unsubscribe(Long trackId) {
        Long userId = SecurityUtils.getCurrentUserId();
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found with id: " + userId));

        TrackEntity track = trackRepository.findById(trackId)
                .orElseThrow(() -> new NotFoundException("Track not found with id: " + trackId));

        user.getSubscribedTracks().remove(track);
        userRepository.save(user);

        log.info("User {} unsubscribed from track {}", userId, trackId);
    }

    @Transactional
    public void unpublish(Long trackId) {
        TrackEntity track = trackRepository.findById(trackId)
                .orElseThrow(() -> new NotFoundException("Track not found with id: " + trackId));

        if (!track.getOwner().getId().equals(SecurityUtils.getCurrentUserId())) {
            throw new ForbiddenException("You can only unpublish tracks you own");
        }

        TrackShareEntity share = track.getTrackShare();
        if (share == null) {
            throw new NotFoundException("Track is not published");
        }

        track.setTrackShare(null);
        share.setTrack(null);

        trackRepository.save(track);

        log.info("Track {} unpublished", trackId);
    }

}

