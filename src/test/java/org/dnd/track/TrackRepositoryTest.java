package org.dnd.track;


import jakarta.transaction.Transactional;
import org.dnd.DatabaseBase;
import org.dnd.group.GroupEntity;
import org.dnd.group.GroupRepository;
import org.dnd.user.UserEntity;
import org.dnd.user.UserHelper;
import org.dnd.user.UserRepository;
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
    UserEntity owner = UserHelper.createValidatedUser("owner", "pw", "email@email.com");
    owner = userRepository.save(owner);

    UserEntity viewer = UserHelper.createValidatedUser("viewer", "pw", "email@email.com");
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
    track.setTrackOriginalName("name");
    track.getGroups().add(group);
    track = trackRepository.save(track);

    List<TrackEntity> ownerTracks = trackRepository.findByOwner_Id(owner.getId());
    assertThat(ownerTracks).hasSize(1);

    List<TrackEntity> accessibleForViewerBefore = trackRepository.findAccessibleTracksForUser(viewer.getId());
    assertThat(accessibleForViewerBefore).isEmpty();

    List<TrackEntity> accessibleForViewerAfter = trackRepository.findAccessibleTracksForUser(owner.getId());
    assertThat(accessibleForViewerAfter)
            .extracting(TrackEntity::getId)
            .containsExactly(track.getId());
  }
}

