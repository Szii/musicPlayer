package org.dnd.repository;

import jakarta.transaction.Transactional;
import org.dnd.model.GroupEntity;
import org.dnd.model.TrackEntity;
import org.dnd.model.UserEntity;
import org.dnd.model.UserTrackAccessEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DirtiesContext
class UserTrackAccessRepositoryTest extends DatabaseBase {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private TrackRepository trackRepository;

    @Autowired
    private UserTrackAccessRepository accessRepository;

    @Test
    @Transactional
    void shareAndUnshareTrackForUser() {
        UserEntity owner = new UserEntity();
        owner.setName("ownerShare");
        owner.setPassword("pw");
        owner = userRepository.save(owner);

        UserEntity viewer = new UserEntity();
        viewer.setName("viewerShare");
        viewer.setPassword("pw");
        viewer = userRepository.save(viewer);

        GroupEntity group = new GroupEntity();
        group.setListName("Shared Group");
        group.setOwner(owner);
        group = groupRepository.save(group);

        TrackEntity track = new TrackEntity();
        track.setTrackName("Shared Track");
        track.setTrackLink("https://example.com/shared.mp3");
        track.setDuration(200);
        track.setOwner(owner);
        track.setGroup(group);
        track = trackRepository.save(track);

        UserTrackAccessEntity access = new UserTrackAccessEntity();
        access.setUser(viewer);
        access.setTrack(track);
        access.setGroup(group);
        accessRepository.save(access);

        List<UserTrackAccessEntity> forViewer = accessRepository.findByUser_Id(viewer.getId());
        assertThat(forViewer).hasSize(1);

        accessRepository.deleteByUser_IdAndTrack_Id(viewer.getId(), track.getId());

        assertThat(accessRepository.findByUser_Id(viewer.getId())).isEmpty();
        assertThat(accessRepository.findByTrack_Id(track.getId())).isEmpty();
    }
}

