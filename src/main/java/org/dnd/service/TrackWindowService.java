package org.dnd.service;

import com.github.dockerjava.api.exception.BadRequestException;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dnd.api.model.Track;
import org.dnd.api.model.TrackWindow;
import org.dnd.api.model.TrackWindowRequest;
import org.dnd.exception.ForbiddenException;
import org.dnd.exception.NotFoundException;
import org.dnd.mappers.TrackMapper;
import org.dnd.mappers.TrackWindowMapper;
import org.dnd.model.TrackEntity;
import org.dnd.model.TrackWindowEntity;
import org.dnd.repository.TrackRepository;
import org.dnd.repository.TrackWindowRepository;
import org.dnd.utils.SecurityUtils;
import org.springframework.stereotype.Service;

@AllArgsConstructor
@Service
@Slf4j
public class TrackWindowService {
    private final TrackRepository trackRepository;
    private final TrackWindowMapper trackWindowMapper;
    private final TrackWindowRepository trackWindowRepository;
    private final TrackMapper trackMapper;

    @Transactional
    public Track deleteTrackWindow(Long trackId, Long windowId) {
        log.debug("Deleting track point {} from track {}", windowId, trackId);

        TrackEntity track = trackRepository.findById(trackId)
                .orElseThrow(() -> new NotFoundException("Track with id %d not found".formatted(trackId)));

        if (!track.getOwner().getId().equals(SecurityUtils.getCurrentUserId())) {
            throw new ForbiddenException("You can only delete track points from your own tracks");
        }

        TrackWindowEntity point = trackWindowRepository.findByIdAndTrack_Id(windowId, trackId)
                .orElseThrow(() -> new NotFoundException(
                        "Track point with id %d not found for track %d".formatted(windowId, trackId)));

        track.getTrackWindows().remove(point);

        return trackMapper.toDto(trackRepository.save(track));
    }

    @Transactional
    public Track updateTrackWindow(Long trackId, Long windowId, TrackWindowRequest trackWindowRequest) {
        TrackEntity track = trackRepository.findById(trackId)
                .orElseThrow(() -> new NotFoundException(String.format("Track with id %d not found", trackId)));

        if (!track.getOwner().getId().equals(SecurityUtils.getCurrentUserId())) {
            throw new ForbiddenException("You can only update track points on your own tracks");
        }

        TrackWindowEntity entity = trackWindowRepository.findByIdAndTrack_Id(windowId, trackId)
                .orElseThrow(() -> new NotFoundException(String.format("Track point with id %d not found", windowId)));

        trackWindowMapper.updateFromRequest(trackWindowRequest, entity);
        trackWindowRepository.save(entity);
        log.debug("Updated track point with id {} for track {}", windowId, trackId);

        return trackMapper.toDto(track);
    }

    public Track createTrackWindow(Long trackId, TrackWindowRequest trackWindowRequest) {
        TrackEntity track = trackRepository.findById(trackId)
                .orElseThrow(() -> new NotFoundException(String.format("Track with id %d not found", trackId)));

        if (!track.getOwner().getId().equals(SecurityUtils.getCurrentUserId())) {
            throw new ForbiddenException("You can only create track windows on your own tracks");
        }

        if (trackWindowRequest.getPositionFrom() < 0 || trackWindowRequest.getPositionTo() > track.getDuration()) {
            throw new BadRequestException("Track window position must be within track duration");
        }

        if (trackWindowRequest.getPositionFrom() > trackWindowRequest.getPositionTo()) {
            throw new BadRequestException("Track window from position must be greater than track window to position");
        }

        TrackWindowEntity entity = trackWindowMapper.fromRequest(trackWindowRequest, track);
        trackWindowRepository.save(entity);
        log.debug("Created track point with id {} for track {}", entity.getId(), trackId);

        return trackMapper.toDto(track);
    }

    public TrackWindow getTrackWindow(Long trackId, Long pointId) {
        TrackEntity track = trackRepository.findById(trackId)
                .orElseThrow(() -> new NotFoundException(String.format("Track with id %d not found", trackId)));
        if (!track.getOwner().getId().equals(SecurityUtils.getCurrentUserId())) {
            throw new ForbiddenException("You can only get track points for your own tracks");
        }
        TrackWindowEntity entity = trackWindowRepository.findByIdAndTrack_Id(pointId, trackId)
                .orElseThrow(() -> new NotFoundException(String.format("Track point with id %d not found", pointId)));

        log.debug("Retrieved track point with id {} for track {}", entity.getId(), trackId);
        return trackWindowMapper.toDto(entity);
    }
}
