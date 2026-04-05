package org.dnd.service.playback;

import com.sedmelluq.discord.lavaplayer.format.StandardAudioDataFormats;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dnd.service.JwtService;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class StreamSessionsManager {

  private final AudioPlayerManager playerManager;
  private final JwtService jwtService;

  private final ConcurrentMap<Long, StreamSession> boardSessions = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, StreamSession> trackSessions = new ConcurrentHashMap<>();

  private ExecutorService workers;
  private ScheduledExecutorService scheduler;

  @PostConstruct
  void init() {
    playerManager.getConfiguration().setOutputFormat(StandardAudioDataFormats.DISCORD_PCM_S16_BE);

    workers = Executors.newCachedThreadPool(r -> {
      Thread t = new Thread(r, "playback-worker");
      t.setDaemon(true);
      return t;
    });

    scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
      Thread t = new Thread(r, "playback-session-cleanup");
      t.setDaemon(true);
      return t;
    });
  }

  @PreDestroy
  void shutdown() {
    boardSessions.values().forEach(StreamSession::stop);
    boardSessions.clear();

    trackSessions.values().forEach(StreamSession::stop);
    trackSessions.clear();

    workers.shutdownNow();
    scheduler.shutdownNow();
  }

  public Optional<StreamSession> getBoardSession(long boardId) {
    return Optional.ofNullable(boardSessions.get(boardId));
  }

  public Optional<StreamSession> getTrackSession(long userId, long trackId) {
    return Optional.ofNullable(trackSessions.get(trackSessionKey(userId, trackId)));
  }

  public StreamSession startBoardSession(long boardId,
                                         long trackId,
                                         String trackLink,
                                         int duration,
                                         Long windowStartS,
                                         Long windowEndS) {
    StreamSession session = newBoardSession(boardId);

    StreamSession previous = boardSessions.put(boardId, session);
    if (previous != null) {
      previous.stop();
    }

    try {
      session.setWindow(windowStartS, windowEndS);
      session.loadAndPlay(trackId, trackLink, duration);
      return session;
    } catch (RuntimeException ex) {
      boardSessions.remove(boardId, session);
      session.stop();
      throw ex;
    }
  }

  public void stopBoardSession(long boardId) {
    StreamSession session = boardSessions.remove(boardId);
    if (session != null) {
      session.stop();
    }
  }

  public StreamSession startTrackSession(long userId,
                                         long trackId,
                                         String trackLink,
                                         int duration,
                                         Long windowStartS,
                                         Long windowEndS) {
    String key = trackSessionKey(userId, trackId);
    StreamSession existing = trackSessions.get(key);

    if (existing != null
            && existing.isReusableForReplay()
            && windowStartS == null
            && windowEndS == null) {
      existing.activateForReplay();
      return existing;
    }

    StreamSession session = newTrackSession(trackId, key);

    StreamSession previous = trackSessions.put(key, session);
    if (previous != null) {
      previous.stop();
    }

    try {
      session.setWindow(windowStartS, windowEndS);
      session.loadAndPlay(trackId, trackLink, duration);
      return session;
    } catch (RuntimeException ex) {
      trackSessions.remove(key, session);
      session.stop();
      throw ex;
    }
  }

  public StreamSession getOrCreateTrackSessionForWaveform(long userId,
                                                          long trackId,
                                                          String trackLink,
                                                          int duration) {
    String key = trackSessionKey(userId, trackId);

    StreamSession existing = trackSessions.get(key);
    if (existing != null) {
      return existing;
    }

    StreamSession created = newTrackSession(trackId, key);
    StreamSession raced = trackSessions.putIfAbsent(key, created);
    if (raced != null) {
      return raced;
    }

    try {
      created.loadAndPlay(trackId, trackLink, duration);
      return created;
    } catch (RuntimeException ex) {
      trackSessions.remove(key, created);
      created.stop();
      throw ex;
    }
  }

  StreamSession newBoardSession(long boardId) {
    return new StreamSession(
            boardId,
            false,
            playerManager,
            jwtService,
            workers,
            scheduler,
            session -> boardSessions.remove(boardId, session)
    );
  }

  StreamSession newTrackSession(long trackId, String key) {
    return new StreamSession(
            trackId,
            true,
            playerManager,
            jwtService,
            workers,
            scheduler,
            session -> trackSessions.remove(key, session)
    );
  }

  private static String trackSessionKey(long userId, long trackId) {
    return userId + ":" + trackId;
  }
}