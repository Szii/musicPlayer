package org.dnd.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dnd.api.model.GroupShare;
import org.dnd.api.model.TrackShare;
import org.dnd.exception.NotFoundException;
import org.dnd.mappers.GroupMapper;
import org.dnd.mappers.TrackMapper;
import org.dnd.model.GroupEntity;
import org.dnd.model.TrackEntity;
import org.dnd.model.UserEntity;
import org.dnd.model.UserTrackAccessEntity;
import org.dnd.repository.GroupRepository;
import org.dnd.repository.TrackRepository;
import org.dnd.repository.UserRepository;
import org.dnd.repository.UserTrackAccessRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class TrackShareService {
    private final UserTrackAccessRepository userTrackAccessRepository;

    private final TrackRepository trackRepository;

    private final UserRepository userRepository;

    private final GroupRepository groupRepository;

    private final TrackMapper trackMapper;

    private final GroupMapper groupMapper;


    public List<TrackShare> getTrackShares(Long trackId) {
        List<UserTrackAccessEntity> shares = userTrackAccessRepository.findByTrack_Id(trackId);

        return shares.stream()
                .map(share -> trackMapper.toShareDto(share.getTrack()))
                .collect(Collectors.toList());
    }

    public List<GroupShare> getGroupShares(Long groupId, Long userId) {
        List<UserTrackAccessEntity> shares = userTrackAccessRepository.findByTrack_Id(groupId);

        return shares.stream()
                .map(share -> groupMapper.toShareDto(share.getGroup()))
                .collect(Collectors.toList());
    }

    public void shareGroup(Long groupId, Long userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException(String.format("User with id %d not found", userId)));
        GroupEntity group = groupRepository.findById(groupId)
                .orElseThrow(() -> new NotFoundException(String.format("Group with id %d not found", groupId)));

        UserTrackAccessEntity access = new UserTrackAccessEntity();
        access.setUser(user);
        access.setGroup(group);
        userTrackAccessRepository.save(access);
    }


    public void shareTrack(Long trackId, Long userId) {
        TrackEntity entity = trackRepository.findById(trackId)
                .orElseThrow(() -> new NotFoundException(String.format("Track with id %d not found", trackId)));
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException(String.format("User with id %d not found", userId)));

        UserTrackAccessEntity access = new UserTrackAccessEntity();
        access.setUser(user);
        access.setTrack(entity);
        userTrackAccessRepository.save(access);
    }


    public void unshareTrack(Long trackId, Long userId) {
        userTrackAccessRepository.findByUser_IdAndTrack_Id(userId, trackId).ifPresent(userTrackAccessRepository::delete);
    }

    public void unshareGroup(Long groupId, Long userId) {
        userTrackAccessRepository.findByUser_IdAndGroup_Id(userId, groupId).ifPresent(userTrackAccessRepository::delete);
    }

}
