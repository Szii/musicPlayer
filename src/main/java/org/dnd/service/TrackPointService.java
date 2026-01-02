package org.dnd.service;

import com.github.dockerjava.api.exception.BadRequestException;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dnd.api.model.Track;
import org.dnd.api.model.TrackPoint;
import org.dnd.api.model.TrackPointRequest;
import org.dnd.exception.ForbiddenException;
import org.dnd.exception.NotFoundException;
import org.dnd.mappers.TrackMapper;
import org.dnd.mappers.TrackPointMapper;
import org.dnd.model.TrackEntity;
import org.dnd.model.TrackPointEntity;
import org.dnd.repository.TrackPointRepository;
import org.dnd.repository.TrackRepository;
import org.dnd.utils.SecurityUtils;
import org.springframework.stereotype.Service;

@AllArgsConstructor
@Service
@Slf4j
public class TrackPointService {
    private final TrackRepository trackRepository;
    private final TrackPointMapper trackPointMapper;
    private final TrackPointRepository trackPointRepository;
    private final TrackMapper trackMapper;

    @Transactional
    public Track deleteTrackPoint(Long trackId, Long pointId) {
        log.debug("Deleting track point {} from track {}", pointId, trackId);

        TrackEntity track = trackRepository.findById(trackId)
                .orElseThrow(() -> new NotFoundException("Track with id %d not found".formatted(trackId)));

        if (!track.getOwner().getId().equals(SecurityUtils.getCurrentUserId())) {
            throw new ForbiddenException("You can only delete track points from your own tracks");
        }

        TrackPointEntity point = trackPointRepository.findByIdAndTrack_Id(pointId, trackId)
                .orElseThrow(() -> new NotFoundException(
                        "Track point with id %d not found for track %d".formatted(pointId, trackId)));

        track.getTrackPoints().remove(point);
        
        return trackMapper.toDto(trackRepository.save(track));
    }

    @Transactional
    public Track updateTrackPoint(Long trackId, Long pointId, TrackPointRequest trackPoint) {
        TrackEntity track = trackRepository.findById(trackId)
                .orElseThrow(() -> new NotFoundException(String.format("Track with id %d not found", trackId)));

        if (!track.getOwner().getId().equals(SecurityUtils.getCurrentUserId())) {
            throw new ForbiddenException("You can only update track points on your own tracks");
        }

        TrackPointEntity entity = trackPointRepository.findByIdAndTrack_Id(pointId, trackId)
                .orElseThrow(() -> new NotFoundException(String.format("Track point with id %d not found", pointId)));

        trackPointMapper.updateFromRequest(trackPoint, entity);
        trackPointRepository.save(entity);
        log.debug("Updated track point with id {} for track {}", pointId, trackId);

        return trackMapper.toDto(track);
    }

    public Track createTrackPoint(Long trackId, TrackPointRequest trackPoint) {
        TrackEntity track = trackRepository.findById(trackId)
                .orElseThrow(() -> new NotFoundException(String.format("Track with id %d not found", trackId)));

        if (!track.getOwner().getId().equals(SecurityUtils.getCurrentUserId())) {
            throw new ForbiddenException("You can only create track points on your own tracks");
        }

        if (trackPoint.getPosition() < 0 || trackPoint.getPosition() > track.getDuration()) {
            throw new BadRequestException("Track point position must be within track duration");
        }

        TrackPointEntity entity = trackPointMapper.fromRequest(trackPoint, track);
        trackPointRepository.save(entity);
        log.debug("Created track point with id {} for track {}", entity.getId(), trackId);

        return trackMapper.toDto(track);
    }

    public TrackPoint getTrackPoint(Long trackId, Long pointId) {
        TrackEntity track = trackRepository.findById(trackId)
                .orElseThrow(() -> new NotFoundException(String.format("Track with id %d not found", trackId)));
        if (!track.getOwner().getId().equals(SecurityUtils.getCurrentUserId())) {
            throw new ForbiddenException("You can only get track points for your own tracks");
        }
        TrackPointEntity entity = trackPointRepository.findByIdAndTrack_Id(pointId, trackId)
                .orElseThrow(() -> new NotFoundException(String.format("Track point with id %d not found", pointId)));

        log.debug("Retrieved track point with id {} for track {}", entity.getId(), trackId);
        return trackPointMapper.toDto(entity);
    }
}
