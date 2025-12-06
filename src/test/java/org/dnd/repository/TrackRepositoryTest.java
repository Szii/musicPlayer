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
class TrackRepositoryTest extends DatabaseBase {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private TrackRepository trackRepository;

    @Autowired
    private UserTrackAccessRepository userTrackAccessRepository;


    @Test
    @Transactional
    void ownerAndViewerAccessibleTracks() {
        UserEntity owner = new UserEntity();
        owner.setName("owner");
        owner.setPassword("pw");
        owner = userRepository.save(owner);

        UserEntity viewer = new UserEntity();
        viewer.setName("viewer");
        viewer.setPassword("pw");
        viewer = userRepository.save(viewer);

        GroupEntity group = new GroupEntity();
        group.setListName("Group A");
        group.setOwner(owner);
        group = groupRepository.save(group);

        TrackEntity track = new TrackEntity();
        track.setTrackName("Track A");
        track.setTrackLink("https://example.com/a.mp3");
        track.setDuration(120);
        track.setOwner(owner);
        track.setGroup(group);
        track = trackRepository.save(track);

        List<TrackEntity> ownerTracks = trackRepository.findByOwner_Id(owner.getId());
        assertThat(ownerTracks).hasSize(1);

        List<TrackEntity> accessibleForViewerBefore = trackRepository.findAccessibleTracksForUser(viewer.getId());
        assertThat(accessibleForViewerBefore).isEmpty();

        UserTrackAccessEntity access = new UserTrackAccessEntity();
        access.setUser(viewer);
        access.setTrack(track);
        access.setGroup(group);
        userTrackAccessRepository.save(access);

        List<TrackEntity> accessibleForViewerAfter = trackRepository.findAccessibleTracksForUser(viewer.getId());
        assertThat(accessibleForViewerAfter)
                .extracting(TrackEntity::getId)
                .containsExactly(track.getId());
    }
}

