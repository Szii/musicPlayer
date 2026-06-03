package org.dnd.track;

import jakarta.transaction.Transactional;
import org.dnd.DatabaseBase;
import org.dnd.user.UserEntity;
import org.dnd.user.UserHelper;
import org.dnd.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DirtiesContext
class TrackWindowRepositoryTest extends DatabaseBase {
  @Autowired
  private UserRepository userRepository;

  @Autowired
  private TrackRepository trackRepository;

  @Autowired
  private TrackWindowRepository trackWindowRepository;

  @Test
  @Transactional
  void createUpdateAndDeleteTrackWindow() {
    UserEntity owner = UserHelper.createValidatedUser("owner", "pw", "user@email.com");
    owner = userRepository.save(owner);

    TrackEntity track = new TrackEntity();
    track.setTrackName("Track with points");
    track.setTrackLink("https://example.com/points.mp3");
    track.setDuration(200);
    track.setOwner(owner);
    track.setTrackOriginalName("name");
    track = trackRepository.save(track);

    TrackWindowEntity window = new TrackWindowEntity();
    window.setTrack(track);
    window.setName("Intro");
    window.setPositionFrom(10L);
    window.setPositionTo(10L);
    window.setFadeOutDurationMs(1000);
    window.setFadeInDurationMs(1000);
    window = trackWindowRepository.save(window);

    List<TrackWindowEntity> windows = trackWindowRepository.findByTrack_Id(track.getId());
    assertThat(windows).hasSize(1);

    Optional<TrackWindowEntity> loaded = trackWindowRepository.findByIdAndTrack_Id(window.getId(), track.getId());
    assertThat(loaded).isPresent();
    loaded.get().setName("Intro updated");
    trackWindowRepository.save(loaded.get());

    TrackWindowEntity updated = trackWindowRepository.findById(window.getId()).orElseThrow();
    assertThat(updated.getName()).isEqualTo("Intro updated");

    trackWindowRepository.deleteByIdAndTrack_Id(window.getId(), track.getId());
    assertThat(trackWindowRepository.findByTrack_Id(track.getId())).isEmpty();
  }
}

