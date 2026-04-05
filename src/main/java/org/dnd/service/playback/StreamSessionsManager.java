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
  private final ConcurrentMap<String, WaveformSession> waveformSessions = new ConcurrentHashMap<>();

  private ExecutorService decodeWorkers;
  private ExecutorService streamIoWorkers;
  private ScheduledExecutorService scheduler;

  @PostConstruct
  void init() {
    playerManager.getConfiguration().setOutputFormat(StandardAudioDataFormats.DISCORD_PCM_S16_BE);

    ThreadFactory decodeFactory = Thread.ofVirtual()
            .name("playback-decode-", 0)
            .factory();

    ThreadFactory streamIoFactory = Thread.ofVirtual()
            .name("playback-stream-", 0)
            .factory();

    ThreadFactory cleanupFactory = Thread.ofPlatform()
            .name("playback-cleanup-", 0)
            .daemon(true)
            .factory();

    decodeWorkers = Executors.newThreadPerTaskExecutor(decodeFactory);
    streamIoWorkers = Executors.newThreadPerTaskExecutor(streamIoFactory);
    scheduler = Executors.newSingleThreadScheduledExecutor(cleanupFactory);
  }

  @PreDestroy
  void shutdown() {
    boardSessions.values().forEach(StreamSession::stop);
    boardSessions.clear();

    trackSessions.values().forEach(StreamSession::stop);
    trackSessions.clear();

    waveformSessions.values().forEach(WaveformSession::stop);
    waveformSessions.clear();

    shutdownExecutor(decodeWorkers, "decodeWorkers");
    shutdownExecutor(streamIoWorkers, "streamIoWorkers");
    shutdownExecutor(scheduler, "scheduler");
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
      session.loadAndPlay(trackId, trackLink, duration, windowStartS, windowEndS);
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
    StreamSession session = newTrackSession(trackId, key);

    StreamSession previous = trackSessions.put(key, session);
    if (previous != null) {
      previous.stop();
    }

    try {
      session.loadAndPlay(trackId, trackLink, duration, windowStartS, windowEndS);
      return session;
    } catch (RuntimeException ex) {
      trackSessions.remove(key, session);
      session.stop();
      throw ex;
    }
  }

  public WaveformSession getOrCreateWaveformSession(long userId,
                                                    long trackId,
                                                    String trackLink,
                                                    int duration) {
    String key = waveformSessionKey(userId, trackId);

    WaveformSession existing = waveformSessions.get(key);
    if (existing != null) {
      return existing;
    }

    WaveformSession created = newWaveformSession(trackId, key);
    WaveformSession raced = waveformSessions.putIfAbsent(key, created);
    if (raced != null) {
      return raced;
    }

    try {
      created.loadAndAnalyze(trackId, trackLink, duration);
      return created;
    } catch (RuntimeException ex) {
      waveformSessions.remove(key, created);
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
            decodeWorkers,
            streamIoWorkers,
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
            decodeWorkers,
            streamIoWorkers,
            scheduler,
            session -> trackSessions.remove(key, session)
    );
  }

  WaveformSession newWaveformSession(long trackId, String key) {
    return new WaveformSession(
            trackId,
            playerManager,
            decodeWorkers,
            scheduler,
            session -> waveformSessions.remove(key, session)
    );
  }

  private static String trackSessionKey(long userId, long trackId) {
    return userId + ":" + trackId;
  }

  private static String waveformSessionKey(long userId, long trackId) {
    return userId + ":" + trackId;
  }

  private static void shutdownExecutor(ExecutorService executor, String name) {
    if (executor == null) {
      return;
    }

    executor.shutdownNow();
    try {
      if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
        log.warn("Executor {} did not terminate within timeout", name);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.warn("Interrupted while shutting down executor {}", name);
    }
  }
}