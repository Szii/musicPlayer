package org.dnd.service.playback;

import com.sedmelluq.discord.lavaplayer.player.AudioConfiguration;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import org.dnd.service.JwtService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StreamSessionsManagerTest {

  @Mock
  private AudioPlayerManager playerManager;

  @Mock
  private JwtService jwtService;

  @Mock
  private AudioConfiguration audioConfiguration;

  private StreamSessionsManager manager;
  private AutoCloseable mocks;

  @BeforeEach
  void setUp() {
    mocks = MockitoAnnotations.openMocks(this);

    when(playerManager.getConfiguration()).thenReturn(audioConfiguration);
    doNothing().when(audioConfiguration).setOutputFormat(any());

    manager = new StreamSessionsManager(playerManager, jwtService);
    manager.init();
  }

  @AfterEach
  void tearDown() throws Exception {
    if (manager != null) {
      manager.shutdown();
    }
    if (mocks != null) {
      mocks.close();
    }
  }

  @Test
  void boardSessionLifecycleShouldWorkCorrectly() {
    StreamSession session = mock(StreamSession.class);

    StreamSessionsManager spyManager = spy(manager);
    doReturn(session).when(spyManager).newBoardSession(1L);
    doNothing().when(session).loadAndPlay(anyLong(), anyString(), anyInt(), any(), any());
    doNothing().when(session).stop();

    StreamSession created = spyManager.startBoardSession(1L, 2L, "link", 100, null, null);

    assertSame(session, created);

    Optional<StreamSession> found = spyManager.getBoardSession(1L);
    assertTrue(found.isPresent());
    assertSame(session, found.get());

    spyManager.stopBoardSession(1L);

    assertTrue(spyManager.getBoardSession(1L).isEmpty());
    verify(session).loadAndPlay(2L, "link", 100, null, null);
    verify(session).stop();
  }

  @Test
  void trackSessionLifecycleShouldNotInterfereWithBoardSessionLifecycle() {
    StreamSession trackSession = mock(StreamSession.class);

    StreamSessionsManager spyManager = spy(manager);
    doReturn(trackSession).when(spyManager).newTrackSession(anyLong(), anyString());
    doNothing().when(trackSession).loadAndPlay(anyLong(), anyString(), anyInt(), any(), any());
    doNothing().when(trackSession).stop();

    StreamSession created = spyManager.startTrackSession(1L, 2L, "link", 100, null, null);

    assertSame(trackSession, created);

    Optional<StreamSession> found = spyManager.getTrackSession(1L, 2L);
    assertTrue(found.isPresent());
    assertSame(trackSession, found.get());

    spyManager.stopBoardSession(1L);

    assertTrue(spyManager.getTrackSession(1L, 2L).isPresent());
    assertSame(trackSession, spyManager.getTrackSession(1L, 2L).orElseThrow());
    verify(trackSession).loadAndPlay(2L, "link", 100, null, null);
  }

  @Test
  void getOrCreateWaveformSessionCreatesAndReuses() {
    WaveformSession waveformSession = mock(WaveformSession.class);

    StreamSessionsManager spyManager = spy(manager);
    doReturn(waveformSession).when(spyManager).newWaveformSession(eq(2L), anyString());
    doNothing().when(waveformSession).loadAndAnalyze(anyLong(), anyString(), anyInt());

    WaveformSession created = spyManager.getOrCreateWaveformSession(1L, 2L, "link", 100);
    WaveformSession reused = spyManager.getOrCreateWaveformSession(1L, 2L, "link", 100);

    assertSame(waveformSession, created);
    assertSame(waveformSession, reused);
    verify(waveformSession, times(1)).loadAndAnalyze(anyLong(), "link", 100);
  }

  @Test
  void startBoardSessionReplacesPreviousSession() {
    StreamSession first = mock(StreamSession.class);
    StreamSession second = mock(StreamSession.class);

    StreamSessionsManager spyManager = spy(manager);
    doReturn(first).doReturn(second).when(spyManager).newBoardSession(1L);

    doNothing().when(first).loadAndPlay(anyLong(), anyString(), anyInt(), any(), any());
    doNothing().when(first).stop();

    doNothing().when(second).loadAndPlay(anyLong(), anyString(), anyInt(), any(), any());
    doNothing().when(second).stop();

    StreamSession created1 = spyManager.startBoardSession(1L, 2L, "link", 100, null, null);
    StreamSession created2 = spyManager.startBoardSession(1L, 3L, "link2", 120, 5L, 10L);

    assertSame(first, created1);
    assertSame(second, created2);

    verify(first).loadAndPlay(2L, "link", 100, null, null);
    verify(second).loadAndPlay(3L, "link2", 120, 5L, 10L);
    verify(first).stop();
  }

  @Test
  void startTrackSessionReplacesPreviousSession() {
    StreamSession first = mock(StreamSession.class);
    StreamSession second = mock(StreamSession.class);

    StreamSessionsManager spyManager = spy(manager);
    doReturn(first).doReturn(second).when(spyManager).newTrackSession(anyLong(), anyString());

    doNothing().when(first).loadAndPlay(anyLong(), anyString(), anyInt(), any(), any());
    doNothing().when(first).stop();

    doNothing().when(second).loadAndPlay(anyLong(), anyString(), anyInt(), any(), any());
    doNothing().when(second).stop();

    StreamSession created1 = spyManager.startTrackSession(1L, 2L, "link", 100, null, null);
    StreamSession created2 = spyManager.startTrackSession(1L, 2L, "link2", 120, 7L, 20L);

    assertSame(first, created1);
    assertSame(second, created2);

    verify(first).loadAndPlay(2L, "link", 100, null, null);
    verify(second).loadAndPlay(2L, "link2", 120, 7L, 20L);
    verify(first).stop();
  }

  @Test
  void startBoardSessionRemovesAndStopsOnException() {
    StreamSession session = mock(StreamSession.class);

    StreamSessionsManager spyManager = spy(manager);
    doReturn(session).when(spyManager).newBoardSession(1L);
    doThrow(new RuntimeException("fail"))
            .when(session).loadAndPlay(anyLong(), anyString(), anyInt(), any(), any());
    doNothing().when(session).stop();

    RuntimeException ex = assertThrows(
            RuntimeException.class,
            () -> spyManager.startBoardSession(1L, 2L, "link", 100, null, null)
    );

    assertEquals("fail", ex.getMessage());
    assertTrue(spyManager.getBoardSession(1L).isEmpty());
    verify(session).stop();
  }

  @Test
  void startTrackSessionRemovesAndStopsOnException() {
    StreamSession session = mock(StreamSession.class);

    StreamSessionsManager spyManager = spy(manager);
    doReturn(session).when(spyManager).newTrackSession(anyLong(), anyString());
    doThrow(new RuntimeException("fail"))
            .when(session).loadAndPlay(anyLong(), anyString(), anyInt(), any(), any());
    doNothing().when(session).stop();

    RuntimeException ex = assertThrows(
            RuntimeException.class,
            () -> spyManager.startTrackSession(1L, 2L, "link", 100, null, null)
    );

    assertEquals("fail", ex.getMessage());
    assertTrue(spyManager.getTrackSession(1L, 2L).isEmpty());
    verify(session).stop();
  }

  @Test
  void getOrCreateWaveformSessionRemovesAndStopsOnException() {
    WaveformSession waveformSession = mock(WaveformSession.class);

    StreamSessionsManager spyManager = spy(manager);
    doReturn(waveformSession).when(spyManager).newWaveformSession(eq(2L), anyString());
    doThrow(new RuntimeException("fail")).when(waveformSession).loadAndAnalyze(anyLong(), anyString(), anyInt());
    doNothing().when(waveformSession).stop();

    RuntimeException ex = assertThrows(
            RuntimeException.class,
            () -> spyManager.getOrCreateWaveformSession(1L, 2L, "link", 100)
    );

    assertEquals("fail", ex.getMessage());
    verify(waveformSession).stop();
  }

  @Test
  void sessionCallbackRemovesBoardSession() {
    StreamSession session = mock(StreamSession.class);

    StreamSessionsManager spyManager = spy(manager);
    doReturn(session).when(spyManager).newBoardSession(1L);
    doNothing().when(session).loadAndPlay(anyLong(), anyString(), anyInt(), any(), any());

    doAnswer(invocation -> {
      spyManager.stopBoardSession(1L);
      return null;
    }).when(session).stop();

    spyManager.startBoardSession(1L, 2L, "link", 100, null, null);
    session.stop();

    assertTrue(spyManager.getBoardSession(1L).isEmpty());
  }

  @Test
  void stopBoardSessionShouldNotAffectDifferentBoardSession() {
    StreamSession first = mock(StreamSession.class);
    StreamSession second = mock(StreamSession.class);

    StreamSessionsManager spyManager = spy(manager);
    doReturn(first).when(spyManager).newBoardSession(1L);
    doReturn(second).when(spyManager).newBoardSession(2L);

    doNothing().when(first).loadAndPlay(anyLong(), anyString(), anyInt(), any(), any());
    doNothing().when(first).stop();

    doNothing().when(second).loadAndPlay(anyLong(), anyString(), anyInt(), any(), any());
    doNothing().when(second).stop();

    spyManager.startBoardSession(1L, 11L, "link1", 100, null, null);
    spyManager.startBoardSession(2L, 22L, "link2", 120, null, null);

    spyManager.stopBoardSession(1L);

    assertTrue(spyManager.getBoardSession(1L).isEmpty());
    assertTrue(spyManager.getBoardSession(2L).isPresent());
    verify(first).stop();
    verify(second, never()).stop();
  }

  @Test
  void startBoardSessionPassesWindowArgumentsToLoadAndPlay() {
    StreamSession session = mock(StreamSession.class);

    StreamSessionsManager spyManager = spy(manager);
    doReturn(session).when(spyManager).newBoardSession(1L);
    doNothing().when(session).loadAndPlay(anyLong(), anyString(), anyInt(), any(), any());

    spyManager.startBoardSession(1L, 99L, "link", 180, 15L, 45L);

    verify(session).loadAndPlay(99L, "link", 180, 15L, 45L);
  }

  @Test
  void startTrackSessionPassesWindowArgumentsToLoadAndPlay() {
    StreamSession session = mock(StreamSession.class);

    StreamSessionsManager spyManager = spy(manager);
    doReturn(session).when(spyManager).newTrackSession(5L, "5:9");
    doNothing().when(session).loadAndPlay(anyLong(), anyString(), anyInt(), any(), any());

    spyManager.startTrackSession(5L, 9L, "track-link", 240, 30L, 90L);

    verify(session).loadAndPlay(9L, "track-link", 240, 30L, 90L);
  }
}