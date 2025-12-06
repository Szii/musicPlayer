package org.dnd.repository;

import jakarta.transaction.Transactional;
import org.dnd.model.TrackEntity;
import org.dnd.model.TrackPointEntity;
import org.dnd.model.UserEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DirtiesContext
class TrackPointRepositoryTest extends DatabaseBase {
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TrackRepository trackRepository;

    @Autowired
    private TrackPointRepository trackPointRepository;

    @Test
    @Transactional
    void createUpdateAndDeleteTrackPoint() {
        UserEntity owner = new UserEntity();
        owner.setName("owner2");
        owner.setPassword("pw");
        owner = userRepository.save(owner);

        TrackEntity track = new TrackEntity();
        track.setTrackName("Track with points");
        track.setTrackLink("https://example.com/points.mp3");
        track.setDuration(200);
        track.setOwner(owner);
        track = trackRepository.save(track);

        TrackPointEntity point = new TrackPointEntity();
        point.setTrack(track);
        point.setName("Intro");
        point.setPosition(10L);
        point.setFadeIn(true);
        point.setFadeOut(false);
        point = trackPointRepository.save(point);

        List<TrackPointEntity> points = trackPointRepository.findByTrack_Id(track.getId());
        assertThat(points).hasSize(1);

        Optional<TrackPointEntity> loaded = trackPointRepository.findByIdAndTrack_Id(point.getId(), track.getId());
        assertThat(loaded).isPresent();
        loaded.get().setName("Intro updated");
        trackPointRepository.save(loaded.get());

        TrackPointEntity updated = trackPointRepository.findById(point.getId()).orElseThrow();
        assertThat(updated.getName()).isEqualTo("Intro updated");

        trackPointRepository.deleteByIdAndTrack_Id(point.getId(), track.getId());
        assertThat(trackPointRepository.findByTrack_Id(track.getId())).isEmpty();
    }
}

