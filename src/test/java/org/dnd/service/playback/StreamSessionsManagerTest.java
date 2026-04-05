package org.dnd.service.playback;

import com.sedmelluq.discord.lavaplayer.player.AudioConfiguration;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import org.dnd.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class StreamSessionsManagerTest {
  @Mock
  private AudioPlayerManager playerManager;
  @Mock
  private JwtService jwtService;
  @Mock
  private ExecutorService workers;
  @Mock
  private ScheduledExecutorService scheduler;
  @Mock
  private AudioConfiguration audioConfiguration;

  private StreamSessionsManager manager;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    when(playerManager.getConfiguration()).thenReturn(audioConfiguration);
    doNothing().when(audioConfiguration).setOutputFormat(any());
    // Mock AudioPlayer creation for real StreamSession
    AudioPlayer mockAudioPlayer = mock(AudioPlayer.class);
    when(playerManager.createPlayer()).thenReturn(mockAudioPlayer);
    doNothing().when(mockAudioPlayer).addListener(any());
    manager = new StreamSessionsManager(playerManager, jwtService);
    manager.init();
  }

  @Test
  void boardSessionLifecycleShouldWorkCorrectly() {
    StreamSession session = mock(StreamSession.class);
    StreamSessionsManager spyManager = spy(manager);
    doReturn(session).when(spyManager).newBoardSession(1L);
    doNothing().when(session).setWindow(any(), any());
    doNothing().when(session).loadAndPlay(anyLong(), anyString(), anyInt());
    doNothing().when(session).stop();

    StreamSession created = spyManager.startBoardSession(1L, 2L, "link", 100, null, null);
    assertSame(session, created);
    Optional<StreamSession> found = spyManager.getBoardSession(1L);
    assertTrue(found.isPresent());
    assertSame(session, found.get());

    spyManager.stopBoardSession(1L);
    assertTrue(spyManager.getBoardSession(1L).isEmpty());
    verify(session).stop();
  }

  @Test
  void trackSessionLifecycleShouldNotInterfereWithBoardSessionLifecycle() {
    StreamSession session = mock(StreamSession.class);
    StreamSessionsManager spyManager = spy(manager);
    doReturn(session).when(spyManager).newTrackSession(anyLong(), anyString());
    doNothing().when(session).setWindow(any(), any());
    doNothing().when(session).loadAndPlay(anyLong(), anyString(), anyInt());
    doNothing().when(session).stop();
    doReturn(false).when(session).isReusableForReplay();

    StreamSession created = spyManager.startTrackSession(1L, 2L, "link", 100, null, null);
    assertSame(session, created);
    Optional<StreamSession> found = spyManager.getTrackSession(1L, 2L);
    assertTrue(found.isPresent());
    assertSame(session, found.get());

    spyManager.stopBoardSession(1L);
    assertTrue(spyManager.getTrackSession(1L, 2L).isPresent());
  }

  @Test
  void getOrCreateTrackSessionForWaveformCreatesAndReuses() {
    StreamSession session = mock(StreamSession.class);
    StreamSessionsManager spyManager = spy(manager);
    doReturn(session).when(spyManager).newTrackSession(anyLong(), anyString());
    doNothing().when(session).loadAndPlay(anyLong(), anyString(), anyInt());

    StreamSession created = spyManager.getOrCreateTrackSessionForWaveform(1L, 2L, "link", 100);
    assertSame(session, created);
    StreamSession reused = spyManager.getOrCreateTrackSessionForWaveform(1L, 2L, "link", 100);
    assertSame(session, reused);
  }

  @Test
  void startTrackSessionReusesSessionIfReusableForReplay() {
    StreamSession session = mock(StreamSession.class);
    when(session.isReusableForReplay()).thenReturn(true);
    StreamSessionsManager spyManager = spy(manager);
    doReturn(session).when(spyManager).newTrackSession(anyLong(), anyString());
    doNothing().when(session).activateForReplay();
    doNothing().when(session).setWindow(any(), any());
    doNothing().when(session).loadAndPlay(anyLong(), anyString(), anyInt());
    // First call creates session
    StreamSession created = spyManager.startTrackSession(1L, 2L, "link", 100, null, null);
    // Second call reuses
    StreamSession reused = spyManager.startTrackSession(1L, 2L, "link", 100, null, null);
    assertSame(created, reused);
    verify(session).activateForReplay();
  }

  @Test
  void startBoardSessionReplacesPreviousSession() {
    StreamSession session1 = mock(StreamSession.class);
    StreamSession session2 = mock(StreamSession.class);
    StreamSessionsManager spyManager = spy(manager);
    doReturn(session1).doReturn(session2).when(spyManager).newBoardSession(1L);
    doNothing().when(session1).setWindow(any(), any());
    doNothing().when(session1).loadAndPlay(anyLong(), anyString(), anyInt());
    doNothing().when(session1).stop();
    doNothing().when(session2).setWindow(any(), any());
    doNothing().when(session2).loadAndPlay(anyLong(), anyString(), anyInt());
    doNothing().when(session2).stop();
    spyManager.startBoardSession(1L, 2L, "link", 100, null, null);
    spyManager.startBoardSession(1L, 2L, "link", 100, null, null);
    verify(session1).stop();
  }

  @Test
  void startTrackSessionReplacesPreviousSession() {
    StreamSession session1 = mock(StreamSession.class);
    StreamSession session2 = mock(StreamSession.class);
    StreamSessionsManager spyManager = spy(manager);
    doReturn(session1).doReturn(session2).when(spyManager).newTrackSession(anyLong(), anyString());
    doNothing().when(session1).setWindow(any(), any());
    doNothing().when(session1).loadAndPlay(anyLong(), anyString(), anyInt());
    doNothing().when(session1).stop();
    doNothing().when(session2).setWindow(any(), any());
    doNothing().when(session2).loadAndPlay(anyLong(), anyString(), anyInt());
    doNothing().when(session2).stop();
    spyManager.startTrackSession(1L, 2L, "link", 100, null, null);
    spyManager.startTrackSession(1L, 2L, "link", 100, null, null);
    verify(session1).stop();
  }

  @Test
  void startBoardSessionRemovesAndStopsOnException() {
    StreamSession session = mock(StreamSession.class);
    StreamSessionsManager spyManager = spy(manager);
    doReturn(session).when(spyManager).newBoardSession(1L);
    doThrow(new RuntimeException("fail")).when(session).setWindow(any(), any());
    doNothing().when(session).stop();
    try {
      spyManager.startBoardSession(1L, 2L, "link", 100, null, null);
    } catch (RuntimeException ignored) {
    }
    assertTrue(spyManager.getBoardSession(1L).isEmpty());
    verify(session).stop();
  }

  @Test
  void startTrackSessionRemovesAndStopsOnException() {
    StreamSession session = mock(StreamSession.class);
    StreamSessionsManager spyManager = spy(manager);
    doReturn(session).when(spyManager).newTrackSession(anyLong(), anyString());
    doThrow(new RuntimeException("fail")).when(session).setWindow(any(), any());
    doNothing().when(session).stop();
    try {
      spyManager.startTrackSession(1L, 2L, "link", 100, null, null);
    } catch (RuntimeException ignored) {
    }
    assertTrue(spyManager.getTrackSession(1L, 2L).isEmpty());
    verify(session).stop();
  }

  @Test
  void sessionCallbackRemovesSession() {
    StreamSessionsManager spyManager = spy(manager);
    StreamSession session = mock(StreamSession.class);
    doReturn(session).when(spyManager).newBoardSession(1L);
    doNothing().when(session).setWindow(any(), any());
    doNothing().when(session).loadAndPlay(anyLong(), anyString(), anyInt());
    doAnswer(invocation -> {
      spyManager.stopBoardSession(1L);
      return null;
    }).when(session).stop();
    spyManager.startBoardSession(1L, 2L, "link", 100, null, null);
    session.stop();
    assertTrue(spyManager.getBoardSession(1L).isEmpty());
  }
}
