package org.dnd.repository;


import jakarta.transaction.Transactional;
import org.dnd.model.GroupEntity;
import org.dnd.model.TrackEntity;
import org.dnd.model.UserEntity;
import org.dnd.model.UserTrackShareEntity;
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
        track.getGroups().add(group);
        track = trackRepository.save(track);

        List<TrackEntity> ownerTracks = trackRepository.findByOwner_Id(owner.getId());
        assertThat(ownerTracks).hasSize(1);

        List<TrackEntity> accessibleForViewerBefore = trackRepository.findAccessibleTracksForUser(viewer.getId());
        assertThat(accessibleForViewerBefore).isEmpty();

        UserTrackShareEntity access = new UserTrackShareEntity();
        access.setUser(viewer);
        access.setTrack(track);

        List<TrackEntity> accessibleForViewerAfter = trackRepository.findAccessibleTracksForUser(owner.getId());
        assertThat(accessibleForViewerAfter)
                .extracting(TrackEntity::getId)
                .containsExactly(track.getId());
    }
}

