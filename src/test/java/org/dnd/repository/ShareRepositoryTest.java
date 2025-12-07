package org.dnd.repository;

import jakarta.transaction.Transactional;
import org.dnd.model.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DirtiesContext
class ShareRepositoryTest extends DatabaseBase {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private TrackRepository trackRepository;

    @Autowired
    private UserTrackShareRepository trackShareRepository;

    @Autowired
    private UserGroupShareRepository groupShareRepository;

    @Test
    @Transactional
    void shareAndUnshareTrackAndGroup() {
        // Setup users
        UserEntity owner = new UserEntity();
        owner.setName("ownerShare");
        owner.setPassword("pw");
        owner = userRepository.save(owner);

        UserEntity viewer = new UserEntity();
        viewer.setName("viewerShare");
        viewer.setPassword("pw");
        viewer = userRepository.save(viewer);

        // Setup group
        GroupEntity group = new GroupEntity();
        group.setListName("Shared Group");
        group.setOwner(owner);
        group = groupRepository.save(group);

        // Setup track
        TrackEntity track = new TrackEntity();
        track.setTrackName("Shared Track");
        track.setTrackLink("https://example.com/shared.mp3");
        track.setDuration(200);
        track.setOwner(owner);
        track.setGroup(group);
        track = trackRepository.save(track);

        // Share track
        UserTrackShareEntity trackShare = new UserTrackShareEntity();
        trackShare.setUser(viewer);
        trackShare.setTrack(track);
        trackShareRepository.save(trackShare);

        // Share group
        UserGroupShareEntity groupShare = new UserGroupShareEntity();
        groupShare.setUser(viewer);
        groupShare.setGroup(group);
        groupShareRepository.save(groupShare);

        // Verify shares exist
        assertThat(trackShareRepository.findByUser_Id(viewer.getId())).hasSize(1);
        assertThat(groupShareRepository.findByUser_Id(viewer.getId())).hasSize(1);

        // Unshare track
        trackShareRepository.deleteByUser_IdAndTrack_Id(viewer.getId(), track.getId());

        // Unshare group
        groupShareRepository.deleteByUser_IdAndGroup_Id(viewer.getId(), group.getId());

        // Verify shares are removed
        assertThat(trackShareRepository.findByUser_Id(viewer.getId())).isEmpty();
        assertThat(trackShareRepository.findByTrack_Id(track.getId())).isEmpty();
        assertThat(groupShareRepository.findByUser_Id(viewer.getId())).isEmpty();
        assertThat(groupShareRepository.findByGroup_Id(group.getId())).isEmpty();
    }
}