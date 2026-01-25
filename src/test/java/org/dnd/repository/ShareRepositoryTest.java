package org.dnd.repository;

import jakarta.transaction.Transactional;
import org.dnd.model.TrackEntity;
import org.dnd.model.TrackShareEntity;
import org.dnd.model.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class ShareRepositoryTest extends DatabaseBase {

    @Autowired
    private TrackShareRepository trackShareRepository;

    @Autowired
    private TrackRepository trackRepository;

    @Autowired
    private UserRepository userRepository;

    private UserEntity testUser;
    private TrackEntity testTrack;
    private TrackShareEntity trackShare;

    @BeforeEach
    @Transactional
    void setUp() {
        trackShareRepository.deleteAll();
        trackRepository.deleteAll();
        userRepository.deleteAll();

        testUser = new UserEntity();
        testUser.setName("testUser_" + System.currentTimeMillis());
        testUser.setPassword("password");
        testUser = userRepository.save(testUser);

        testTrack = new TrackEntity();
        testTrack.setTrackName("Test Track");
        testTrack.setTrackLink("https://example.com/test.mp3");
        testTrack.setDuration(180);
        testTrack.setOwner(testUser);
        testTrack = trackRepository.save(testTrack);

        trackShare = new TrackShareEntity();
        trackShare.setDescription("Test track");
        trackShare.setShareCode("550e8400-e29b-41d4-a716-446655440000");
    }

    @Test
    @Transactional
    void findByShareCode_Found() {
        TrackShareEntity saved = trackShareRepository.save(trackShare);
        testTrack.setTrackShare(saved);
        saved.setTrack(testTrack);
        trackRepository.save(testTrack);


        Optional<TrackShareEntity> result = trackShareRepository.findByShareCode("550e8400-e29b-41d4-a716-446655440000");

        assertTrue(result.isPresent());
        assertEquals("Test track", result.get().getDescription());
        assertEquals("Test Track", result.get().getTrack().getTrackName());
    }

    @Test
    void findByShareCode_NotFound() {
        Optional<TrackShareEntity> result = trackShareRepository.findByShareCode("invalid-code");

        assertFalse(result.isPresent());
    }

    @Test
    @Transactional
    void save_Success() {
        TrackShareEntity saved = trackShareRepository.save(trackShare);
        testTrack.setTrackShare(saved);
        saved.setTrack(testTrack);
        trackRepository.save(testTrack);

        // Retrieve the saved share to verify it provides access to the track
        TrackShareEntity retrieved = trackShareRepository.findById(saved.getId()).orElseThrow();

        assertNotNull(retrieved.getId());
        assertEquals("Test track", retrieved.getDescription());
        assertEquals("550e8400-e29b-41d4-a716-446655440000", retrieved.getShareCode());
        // Verify the TrackShareEntity can provide the associated TrackEntity
        assertNotNull(retrieved.getTrack());
        assertEquals("Test Track", retrieved.getTrack().getTrackName());
    }

    @Test
    @Transactional
    void findById_Success() {
        TrackShareEntity saved = trackShareRepository.save(trackShare);
        testTrack.setTrackShare(saved);
        saved.setTrack(testTrack);
        trackRepository.save(testTrack);

        Optional<TrackShareEntity> result = trackShareRepository.findById(saved.getId());

        assertTrue(result.isPresent());
        assertEquals("Test track", result.get().getDescription());
        assertEquals("Test Track", result.get().getTrack().getTrackName());
    }

    @Test
    void delete_Success() {
        TrackShareEntity saved = trackShareRepository.save(trackShare);

        trackShareRepository.delete(saved);

        Optional<TrackShareEntity> result = trackShareRepository.findById(saved.getId());
        assertFalse(result.isPresent());
    }

    @Test
    @Transactional
    void cascade_DeleteTrackDeletesShare() {
        TrackShareEntity saved = trackShareRepository.save(trackShare);
        Long shareId = saved.getId();
        testTrack.setTrackShare(saved);
        saved.setTrack(testTrack);
        trackRepository.save(testTrack);

        trackRepository.delete(testTrack);

        Optional<TrackShareEntity> result = trackShareRepository.findById(shareId);
        assertFalse(result.isPresent());
    }

    @Test
    @Transactional
    void oneToOneRelationship_OneSharePerTrack() {
        TrackShareEntity saved = trackShareRepository.save(trackShare);
        testTrack.setTrackShare(saved);
        saved.setTrack(testTrack);
        trackRepository.save(testTrack);

        TrackEntity updatedTrack = trackRepository.findById(testTrack.getId()).orElseThrow();
        assertNotNull(updatedTrack.getTrackShare());
        assertEquals(saved.getId(), updatedTrack.getTrackShare().getId());
    }
}
