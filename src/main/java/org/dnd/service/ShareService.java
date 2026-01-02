package org.dnd.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dnd.api.model.GroupShare;
import org.dnd.api.model.TrackShare;
import org.dnd.exception.ForbiddenException;
import org.dnd.exception.NotFoundException;
import org.dnd.mappers.GroupMapper;
import org.dnd.mappers.TrackMapper;
import org.dnd.model.*;
import org.dnd.repository.*;
import org.dnd.utils.SecurityUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ShareService {
    private final UserTrackShareRepository userTrackShareRepository;

    private final UserGroupShareRepository userGroupShareRepository;

    private final TrackRepository trackRepository;

    private final UserRepository userRepository;

    private final GroupRepository groupRepository;

    private final TrackMapper trackMapper;

    private final GroupMapper groupMapper;


    public List<TrackShare> getTrackShares(Long trackId) {
        TrackEntity track = trackRepository.findById(trackId).orElseThrow(() -> new NotFoundException(String.format("Track with id %d not found", trackId)));
        if (track.getOwner().getId().equals(SecurityUtils.getCurrentUserId())) {
            List<UserTrackShareEntity> shares = track.getShares();
            return shares.stream()
                    .map(share -> trackMapper.toShareDto(share.getTrack()))
                    .collect(Collectors.toList());
        } else {
            throw new ForbiddenException("Only the track owner can get track shares");
        }

    }

    public List<GroupShare> getGroupShares(Long groupId) {
        GroupEntity entity = groupRepository.findById(groupId).orElseThrow(() -> new NotFoundException(String.format("Group with id %d not found", groupId)));
        if (entity.getOwner().getId().equals(SecurityUtils.getCurrentUserId())) {
            System.out.println(SecurityUtils.getCurrentUserId() + " " + entity.getOwner().getId());
            List<UserGroupShareEntity> shares = userGroupShareRepository.findByGroup_Id(groupId);
            return shares.stream()
                    .map(groupMapper::toShareDto)
                    .collect(Collectors.toList());
        } else {
            throw new ForbiddenException("Only the group owner can get group shares");
        }

    }

    public void shareGroup(Long groupId, Long userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException(String.format("User with id %d not found", userId)));
        GroupEntity group = groupRepository.findById(groupId)
                .orElseThrow(() -> new NotFoundException(String.format("Group with id %d not found", groupId)));
        if (group.getOwner().getId().equals(SecurityUtils.getCurrentUserId())) {
            if (!userGroupShareRepository.existsByUser_IdAndGroup_Id(userId, groupId)) {
                UserGroupShareEntity access = new UserGroupShareEntity();
                access.setUser(user);
                access.setGroup(group);
                userGroupShareRepository.save(access);
                shareTracksFromGroup(userId, user, group);


            }
        } else {
            throw new ForbiddenException("Only the group owner can share a group");
        }
    }

    public void shareTracksFromGroup(Long userId, UserEntity user, GroupEntity group) {
        for (TrackEntity track : group.getTracks()) {
            if (!userTrackShareRepository.existsByUser_IdAndTrack_Id(userId, track.getId())) {
                UserTrackShareEntity trackAccess = new UserTrackShareEntity();
                trackAccess.setUser(user);
                trackAccess.setTrack(track);
                userTrackShareRepository.save(trackAccess);
                track.getShares().add(trackAccess);
            }
        }
    }


    public void shareTrack(Long trackId, Long userId) {
        TrackEntity entity = trackRepository.findById(trackId)
                .orElseThrow(() -> new NotFoundException(String.format("Track with id %d not found", trackId)));
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException(String.format("User with id %d not found", userId)));

        if (!userTrackShareRepository.existsByUser_IdAndTrack_Id(userId, trackId)) {
            UserTrackShareEntity access = new UserTrackShareEntity();
            access.setUser(user);
            access.setTrack(entity);
            userTrackShareRepository.save(access);
            entity.getShares().add(access);
        }
    }


    @Transactional
    public void unshareTrack(Long trackId, Long userId) {
        Long currentUserId = SecurityUtils.getCurrentUserId();

        TrackEntity track = trackRepository.findById(trackId)
                .orElseThrow(() -> new NotFoundException("Track with id %d not found".formatted(trackId)));

        boolean isSelf = currentUserId.equals(userId);
        boolean isOwner = track.getOwner().getId().equals(currentUserId);

        if (!isSelf && !isOwner) {
            throw new ForbiddenException("Only the track owner can unshare other users");
        }
        track.getShares().removeIf(share -> share.getUser().getId().equals(userId));
    }

    @Transactional
    public void unshareGroup(Long groupId, Long userId) {
        Long currentUserId = SecurityUtils.getCurrentUserId();

        GroupEntity group = groupRepository.findById(groupId)
                .orElseThrow(() -> new NotFoundException(
                        "Group with id %d not found".formatted(groupId)));

        boolean isSelf = currentUserId.equals(userId);
        boolean isOwner = group.getOwner().getId().equals(currentUserId);

        if (!isSelf && !isOwner) {
            throw new ForbiddenException("Only the group owner can unshare other users");
        }

        group.getShares().removeIf(share -> share.getUser().getId().equals(userId));

        for (TrackEntity track : group.getTracks()) {
            track.getShares().removeIf(share -> share.getUser().getId().equals(userId));
        }
    }


}
