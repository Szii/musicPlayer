package org.dnd.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dnd.api.model.Track;
import org.dnd.api.model.TrackRequest;
import org.dnd.exception.ForbiddenException;
import org.dnd.exception.NotFoundException;
import org.dnd.mappers.TrackMapper;
import org.dnd.model.*;
import org.dnd.repository.BoardRepository;
import org.dnd.repository.GroupRepository;
import org.dnd.repository.TrackRepository;
import org.dnd.repository.UserRepository;
import org.dnd.utils.SecurityUtils;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class TrackService {
    private final TrackRepository trackRepository;
    private final UserRepository userRepository;
    private final TrackMetadataService trackMetadataService;
    private final TrackMapper mapper;
    private final BoardRepository boardRepository;
    private final GroupRepository groupRepository;

    @Transactional
    public void deleteTrack(Long trackId) {
        log.debug("Deleting track with id {}", trackId);

        TrackEntity track = trackRepository.findById(trackId)
                .orElseThrow(() -> new NotFoundException(
                        String.format("Track with id %d not found", trackId)));

        if (!track.getOwner().getId().equals(SecurityUtils.getCurrentUserId())) {
            throw new ForbiddenException("You can only delete tracks you own");
        }

        removeShareCompletely(track);
        removeTrackFromAllGroups(trackId);

        boardRepository.clearSelectedTrackFromAllBoards(trackId);
        trackRepository.delete(track);
    }

    public Track addTrack(TrackRequest trackRequest) {
        log.debug("Adding track {}", trackRequest);

        TrackEntity track = mapper.toEntity(trackRequest);
        setTrackMetadata(track, trackRequest);
        track.setOwner(userRepository.getReferenceById(SecurityUtils.getCurrentUserId()));

        return mapper.toDto(trackRepository.save(track));
    }

    private void setTrackMetadata(TrackEntity track, TrackRequest trackRequest) {
        TrackMetadata meta = trackMetadataService.resolveMetadata(trackRequest.getTrackLink());
        track.setTrackOriginalName(meta.title());
        if (track.getTrackName() == null || track.getTrackName().isEmpty()) {
            track.setTrackName(meta.title());
        }
        if (meta.durationSecondsOrNull() != null) {
            track.setDuration(meta.durationSecondsOrNull().intValue());
        }
    }

    @Transactional
    public Track updateTrack(Long trackId, TrackRequest request) {
        log.debug("Updating track with id {}", trackId);
        TrackEntity entity = trackRepository.findById(trackId)
                .orElseThrow(() -> new NotFoundException(
                        String.format("Track with id %d not found", trackId)));

        if (!entity.getOwner().getId().equals(SecurityUtils.getCurrentUserId())) {
            throw new ForbiddenException("You can only update tracks you own");
        }

        mapper.updateTrackFromRequest(request, entity);
        return mapper.toDto(entity);
    }

    public List<Track> getAllTracksForUser() {
        Long userId = SecurityUtils.getCurrentUserId();
        log.debug("Getting tracks for user with id {}", userId);
        return trackRepository.findAccessibleTracksForUser(userId).stream()
                .map(mapper::toDto)
                .collect(Collectors.toList());
    }

    private void removeTrackFromAllGroups(Long trackId) {
        List<GroupEntity> groups = groupRepository.findAllContainingTrack(trackId);
        for (GroupEntity group : groups) {
            group.getTracks().removeIf(t -> t.getId().equals(trackId));
        }
    }

    private void removeShareCompletely(TrackEntity track) {
        TrackShareEntity share = track.getTrackShare();
        if (share == null) {
            return;
        }

        for (UserEntity user : new HashSet<>(share.getUsers())) {
            user.getShares().remove(share);
            share.getUsers().remove(user);
        }

        track.setTrackShare(null);
        share.setTrack(null);
    }
}