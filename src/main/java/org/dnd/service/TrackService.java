package org.dnd.service;

import com.github.dockerjava.api.exception.NotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dnd.api.model.Track;
import org.dnd.api.model.TrackRequest;
import org.dnd.exception.ForbiddenException;
import org.dnd.mappers.TrackMapper;
import org.dnd.model.TrackEntity;
import org.dnd.repository.TrackRepository;
import org.dnd.repository.UserRepository;
import org.dnd.utils.SecurityUtils;
import org.mapstruct.factory.Mappers;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class TrackService {
    private final TrackRepository trackRepository;

    private final UserRepository userRepository;

    TrackMapper mapper = Mappers.getMapper(TrackMapper.class);

    public void deleteTrack(Long trackId) {
        TrackEntity track = trackRepository.findById(trackId)
                .orElseThrow(() -> new org.dnd.exception.NotFoundException(String.format("Track with id %d not found", trackId)));
        if (!track.getOwner().getId().equals(SecurityUtils.getCurrentUserId())) {
            throw new ForbiddenException("You can only delete tracks you own");
        }
        trackRepository.delete(track);
    }

    public Track addTrack(TrackRequest trackRequest) {
        log.debug("Adding track {}", trackRequest);

        return mapper.toDto(trackRepository.save(mapper.toEntity(trackRequest)));
    }

    @Transactional
    public Track updateTrack(Long trackId, TrackRequest request) {
        log.debug("Updating track with id {}", trackId);
        TrackEntity entity = trackRepository.findById(trackId)
                .orElseThrow(() -> new NotFoundException(String.format("Track with id %d not found", trackId)));
        if (!entity.getOwner().getId().equals(SecurityUtils.getCurrentUserId())) {
            throw new ForbiddenException("You can only update tracks you own");
        }
        mapper.updateTrackFromRequest(request, entity);
        return mapper.toDto(trackRepository.save(entity));
    }

    public List<Track> getAllTracksForUser(Long userId) {
        log.debug("Getting tracks for user with id {}", userId);
        return trackRepository.findAccessibleTracksForUser(userId).stream()
                .map(mapper::toDto)
                .collect(Collectors.toList());
    }

}
