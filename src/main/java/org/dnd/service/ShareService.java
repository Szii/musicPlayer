package org.dnd.service;

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
        List<UserTrackShareEntity> shares = userTrackShareRepository.findByTrack_Id(trackId);
        return shares.stream()
                .map(share -> trackMapper.toShareDto(share.getTrack()))
                .collect(Collectors.toList());
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
        }
    }


    public void unshareTrack(Long trackId, Long userId) {
        userTrackShareRepository.findByUser_IdAndTrack_Id(userId, trackId).ifPresent(userTrackShareRepository::delete);
    }

    public void unshareGroup(Long groupId, Long userId) {
        GroupEntity group = groupRepository.findById(groupId)
                .orElseThrow(() -> new NotFoundException(String.format("Group with id %d not found", groupId)));

        if (SecurityUtils.getCurrentUserId().equals(userId) || group.getOwner().getId().equals(SecurityUtils.getCurrentUserId())) {
            userGroupShareRepository.findByUser_IdAndGroup_Id(userId, groupId)
                    .ifPresent(userGroupShareRepository::delete);

            for (TrackEntity track : group.getTracks()) {
                userTrackShareRepository.findByUser_IdAndTrack_Id(userId, track.getId())
                        .ifPresent(userTrackShareRepository::delete);
            }
        } else {
            throw new ForbiddenException("Only the group owner can unshare other users");
        }
    }


}
