package org.dnd.service.playback;

import com.sedmelluq.discord.lavaplayer.format.StandardAudioDataFormats;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import com.sedmelluq.discord.lavaplayer.track.playback.AudioFrame;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dnd.api.model.*;
import org.dnd.exception.NotFoundException;
import org.dnd.model.BoardEntity;
import org.dnd.model.TrackEntity;
import org.dnd.model.UserEntity;
import org.dnd.repository.BoardRepository;
import org.dnd.repository.TrackRepository;
import org.dnd.repository.TrackWindowRepository;
import org.dnd.repository.UserRepository;
import org.dnd.service.JwtService;
import org.dnd.utils.SecurityUtils;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.springframework.http.HttpStatus.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlaybackService {

  private static final int PCM_SAMPLE_RATE = 48000;
  private static final int PCM_CHANNELS = 2;
  private static final int PIPE_BUFFER_BYTES = 256 * 1024;
  private static final long TRACK_LOAD_TIMEOUT_S = 10;
  private static final int WAVEFORM_BUCKETS = 512;
  private static final long TRACK_SESSION_TTL_S = 60;

  private final BoardRepository boardRepository;
  private final TrackWindowRepository trackWindowRepository;
  private final AudioPlayerManager playerManager;
  private final JwtService jwtService;
  private final UserRepository userRepository;
  private final TrackRepository trackRepository;

  private final ConcurrentMap<Long, StreamSession> sessions = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, StreamSession> trackSessions = new ConcurrentHashMap<>();

  private ExecutorService workers;
  private ScheduledExecutorService scheduler;

  @PostConstruct
  private void init() {
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
  private void shutdown() {
    sessions.values().forEach(StreamSession::stop);
    sessions.clear();
    trackSessions.values().forEach(StreamSession::stop);
    trackSessions.clear();
    workers.shutdownNow();
    scheduler.shutdownNow();
  }

  // ── Board playback ────────────────────────────────────────────

  public PlaybackState getState(long boardId) {
    requireOwnedBoard(boardId);
    StreamSession s = sessions.get(boardId);
    return s != null ? s.snapshot() : stoppedBoardState(boardId);
  }

  public PlaybackState playBoard(long boardId, PlayRequest request) {
    BoardEntity board = requireOwnedBoard(boardId);

    TrackEntity track = board.getSelectedTrack();
    if (track == null) throw conflict("No track selected on board");
    if (!validateTrackAccessForCurrentUser(track, true)) throw forbidden("Track not accessible");

    Long windowId = request != null ? request.getWindowId() : null;
    Long windowStartS = null;
    Long windowEndS = null;
    if (windowId != null) {
      var window = trackWindowRepository.findById(windowId)
              .orElseThrow(() -> notFound("Track window not found"));
      windowStartS = window.getPositionFrom();
      windowEndS = window.getPositionTo();
    }

    StreamSession existing = sessions.remove(boardId);
    if (existing != null) existing.stop();

    StreamSession s = new StreamSession(boardId, false);
    sessions.put(boardId, s);
    s.setWindow(windowStartS, windowEndS);
    s.loadAndPlay(track);
    return s.snapshot();
  }

  public PlaybackState stop(long boardId) {
    requireOwnedBoard(boardId);
    StreamSession s = sessions.remove(boardId);
    if (s == null) return stoppedBoardState(boardId);
    s.stop();
    return stoppedBoardState(boardId);
  }

  @Deprecated
  public PlaybackState pause(long boardId) {
    requireOwnedBoard(boardId);
    StreamSession s = sessions.get(boardId);
    return s != null ? s.snapshot() : stoppedBoardState(boardId);
  }

  @Deprecated
  public PlaybackState resume(long boardId) {
    requireOwnedBoard(boardId);
    StreamSession s = sessions.get(boardId);
    return s != null ? s.snapshot() : stoppedBoardState(boardId);
  }

  @Deprecated
  public PlaybackState seek(long boardId, SeekRequest req) {
    requireOwnedBoard(boardId);
    StreamSession s = sessions.get(boardId);
    return s != null ? s.snapshot() : stoppedBoardState(boardId);
  }

  public ResponseEntity<Resource> streamMp3ForUser(long boardId, long userId) {
    requireOwnedBoardByUserId(boardId, userId);
    StreamSession s = sessions.get(boardId);
    if (s == null || !s.canServeStream()) throw conflict("Board is not playing");

    try {
      return s.buildStreamResponse();
    } catch (IOException e) {
      log.error("[board={}] Failed to start stream", boardId, e);
      throw new ResponseStatusException(INTERNAL_SERVER_ERROR, "Streaming failed");
    }
  }

  // ── Track playback ────────────────────────────────────────────

  public PlaybackState playTrack(long trackId, PlayRequest request) {
    TrackEntity track = requireOwnedTrack(trackId);
    Long userId = SecurityUtils.getCurrentUserId();
    String key = trackSessionKey(userId, trackId);

    Long windowId = request != null ? request.getWindowId() : null;
    Long windowStartS = null;
    Long windowEndS = null;
    if (windowId != null) {
      var window = trackWindowRepository.findById(windowId)
              .orElseThrow(() -> notFound("Track window not found"));
      windowStartS = window.getPositionFrom();
      windowEndS = window.getPositionTo();
    }

    StreamSession existing = trackSessions.get(key);

    // Reuse cached full-track session when no saved window playback is requested
    if (existing != null && existing.isReusableForReplay() && windowId == null) {
      existing.activateForReplay();
      return existing.snapshot();
    }

    if (existing != null) {
      trackSessions.remove(key, existing);
      existing.stop();
    }

    StreamSession s = new StreamSession(trackId, true);
    trackSessions.put(key, s);
    s.setWindow(windowStartS, windowEndS);
    s.loadAndPlay(track);
    return s.snapshot();
  }

  public ResponseEntity<Resource> streamMp3ForTrack(long trackId, long userId) {
    TrackEntity track = trackRepository.findById(trackId)
            .orElseThrow(() -> notFound("Track not found"));
    if (!track.getOwner().getId().equals(userId)) throw forbidden("Track not owned by user");

    String key = trackSessionKey(userId, trackId);
    StreamSession s = trackSessions.get(key);
    if (s == null || !s.canServeStream()) throw conflict("Track is not playing");

    try {
      return s.buildStreamResponse();
    } catch (IOException e) {
      log.error("[track={}] Failed to start stream", trackId, e);
      throw new ResponseStatusException(INTERNAL_SERVER_ERROR, "Streaming failed");
    }
  }

  public StreamInfoResponse getTrackStreamInfo(Long trackId) {
    TrackEntity track = requireOwnedTrack(trackId);
    Long userId = SecurityUtils.getCurrentUserId();
    String key = trackSessionKey(userId, trackId);

    StreamSession s = trackSessions.get(key);

    StreamInfoResponse info = new StreamInfoResponse();
    info.setTrackId(trackId);
    info.setMimeType("audio/mpeg");
    info.setRangeSupported(true);

    if (s != null && s.durationMs > 0) {
      info.setDurationS(s.durationMs / 1000L);
    } else if (track.getDuration() != 0) {
      info.setDurationS((long) track.getDuration());
    }

    return info;
  }

  public WaveformResponse getTrackWaveform(Long trackId) {
    TrackEntity track = requireOwnedTrack(trackId);
    Long userId = SecurityUtils.getCurrentUserId();
    String key = trackSessionKey(userId, trackId);

    StreamSession s = trackSessions.get(key);
    if (s == null) {
      s = new StreamSession(trackId, true);
      trackSessions.put(key, s);
      s.loadAndPlay(track);
    }

    s.awaitWaveformWarmup(2, TimeUnit.SECONDS);
    return s.getWaveformResponse();
  }

  // ── Helpers ──────────────────────────────────────────────────

  private static String trackSessionKey(Long userId, long trackId) {
    return userId + ":" + trackId;
  }

  private static PlaybackState stoppedBoardState(long boardId) {
    PlaybackState ps = new PlaybackState();
    ps.setBoardId(boardId);
    ps.setStatus(PlaybackStatus.STOPPED);
    return ps;
  }

  private BoardEntity requireOwnedBoard(long boardId) {
    Long userId = SecurityUtils.getCurrentUserId();
    BoardEntity board = boardRepository.findById(boardId)
            .orElseThrow(() -> notFound("Board not found"));
    if (!board.getOwner().getId().equals(userId)) throw forbidden("Board not owned by user");
    return board;
  }

  private TrackEntity requireOwnedTrack(long trackId) {
    Long userId = SecurityUtils.getCurrentUserId();
    TrackEntity track = trackRepository.findById(trackId)
            .orElseThrow(() -> notFound("Track not found"));
    if (!track.getOwner().getId().equals(userId)) throw forbidden("Track not owned by user");
    return track;
  }

  private void requireOwnedBoardByUserId(long boardId, long userId) {
    BoardEntity board = boardRepository.findById(boardId)
            .orElseThrow(() -> notFound("Board not found"));
    if (!board.getOwner().getId().equals(userId)) throw forbidden("Board not owned by user");
  }

  private static ResponseStatusException conflict(String msg) {
    return new ResponseStatusException(CONFLICT, msg);
  }

  private static ResponseStatusException forbidden(String msg) {
    return new ResponseStatusException(FORBIDDEN, msg);
  }

  private static ResponseStatusException notFound(String msg) {
    return new ResponseStatusException(NOT_FOUND, msg);
  }

  private void removeBoardSession(long boardId, StreamSession expected) {
    sessions.remove(boardId, expected);
  }

  private boolean validateTrackAccessForCurrentUser(TrackEntity track, boolean isSharedTrackValid) {
    UserEntity user = userRepository.findById(SecurityUtils.getCurrentUserId())
            .orElseThrow(() -> new NotFoundException(
                    String.format("User with id %d not found", SecurityUtils.getCurrentUserId()))
            );

    if (isSharedTrackValid) {
      return track.getOwner().getId().equals(SecurityUtils.getCurrentUserId()) ||
              (track.getTrackShare() != null && track.getTrackShare().getUsers().contains(user));
    } else {
      return track.getOwner().getId().equals(SecurityUtils.getCurrentUserId());
    }
  }

  // ── StreamSession ─────────────────────────────────────────────

  private final class StreamSession {

    private final long sessionId;
    private final boolean trackMode;

    private volatile AudioPlayer player;
    volatile PlaybackStatus status = PlaybackStatus.STOPPED;

    private volatile Long currentTrackId;
    private volatile Long windowStartS;
    private volatile Long windowEndS;
    private volatile long durationMs;

    private volatile long streamVersion;
    private volatile boolean trackFinished;
    private volatile boolean fullyDecoded;

    private final PcmBroadcastBuffer pcmBuffer = new PcmBroadcastBuffer();
    private final WaveformAccumulator waveform = new WaveformAccumulator(WAVEFORM_BUCKETS);
    private final CountDownLatch waveformWarmup = new CountDownLatch(1);

    private volatile String cachedStreamToken;
    private volatile long cachedTokenUserId = -1;
    private volatile long cachedTokenVersion = -1;

    private volatile ScheduledFuture<?> expiryFuture;

    private StreamSession(long sessionId, boolean trackMode) {
      this.sessionId = sessionId;
      this.trackMode = trackMode;
    }

    boolean canServeStream() {
      return pcmBuffer.hasHistory() || status == PlaybackStatus.PLAYING;
    }

    boolean isReusableForReplay() {
      return trackMode && fullyDecoded && windowStartS == null && windowEndS == null;
    }

    void activateForReplay() {
      cancelExpiry();
      status = PlaybackStatus.PLAYING;
      streamVersion++;
      cachedStreamToken = null;
    }

    void awaitWaveformWarmup(long timeout, TimeUnit unit) {
      try {
        waveformWarmup.await(timeout, unit);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }

    WaveformResponse getWaveformResponse() {
      return waveform.toResponse(sessionId);
    }

    PlaybackState snapshot() {
      PlaybackState ps = new PlaybackState();
      ps.setStatus(status);
      ps.setTrackId(currentTrackId);
      ps.setWindowStartS(windowStartS);
      ps.setWindowEndS(windowEndS);

      if (trackMode) {
        ps.setBoardId(null);
      } else {
        ps.setBoardId(sessionId);
      }

      AudioPlayer p = player;
      AudioTrack t = p != null ? p.getPlayingTrack() : null;
      ps.setPositionS(t != null ? Math.max(0L, t.getPosition() / 1000L) : null);

      if (canServeStream()) {
        Long userId = SecurityUtils.getCurrentUserId();
        long sv = streamVersion;

        if (cachedStreamToken == null || cachedTokenUserId != userId || cachedTokenVersion != sv) {
          if (trackMode) {
            cachedStreamToken = jwtService.generateTrackStreamToken(userId, sessionId);
          } else {
            cachedStreamToken = jwtService.generateStreamToken(userId, sessionId);
          }
          cachedTokenUserId = userId;
          cachedTokenVersion = sv;
        }

        if (trackMode) {
          ps.setStreamUrl("/tracks/" + sessionId + "/stream?streamToken=" + cachedStreamToken + "&v=" + sv);
        } else {
          ps.setStreamUrl("/boards/" + sessionId + "/stream?streamToken=" + cachedStreamToken + "&v=" + sv);
        }
      }

      return ps;
    }

    void loadAndPlay(TrackEntity trackEntity) {
      stopInternal();

      AudioPlayer newPlayer = playerManager.createPlayer();
      this.player = newPlayer;

      status = PlaybackStatus.PLAYING;
      currentTrackId = trackEntity.getId();
      durationMs = 0L;
      streamVersion++;
      trackFinished = false;
      fullyDecoded = false;
      cachedStreamToken = null;
      pcmBuffer.clear();
      waveform.setDurationMs(1);
      cancelExpiry();

      String label = trackMode ? "track" : "board";

      newPlayer.addListener(new AudioEventAdapter() {
        @Override
        public void onTrackStart(AudioPlayer p, AudioTrack track) {
          status = PlaybackStatus.PLAYING;
          durationMs = Math.max(1L, track.getDuration());
          waveform.setDurationMs(durationMs);
          log.debug("[{}={}] track start: {} ({}ms)", label, sessionId, track.getInfo().title, track.getDuration());
        }

        @Override
        public void onTrackEnd(AudioPlayer p, AudioTrack track, AudioTrackEndReason reason) {
          log.debug("[{}={}] track end: {}", label, sessionId, reason);
          if (reason == AudioTrackEndReason.REPLACED) return;
          trackFinished = true;
          fullyDecoded = true;
          pcmBuffer.markComplete();
        }

        @Override
        public void onTrackStuck(AudioPlayer p, AudioTrack track, long thresholdMs) {
          log.warn("[{}={}] track stuck ({}ms)", label, sessionId, thresholdMs);
          status = PlaybackStatus.ERROR;
          removeThisSession();
        }

        @Override
        public void onTrackException(AudioPlayer p, AudioTrack track, FriendlyException ex) {
          log.error("[{}={}] track exception: {}", label, sessionId, ex.getMessage(), ex);
          status = PlaybackStatus.ERROR;
          removeThisSession();
        }
      });

      CountDownLatch latch = new CountDownLatch(1);

      playerManager.loadItem(trackEntity.getTrackLink(), new AudioLoadResultHandler() {
        @Override
        public void trackLoaded(AudioTrack track) {
          applyWindowStart(track);
          durationMs = Math.max(1L, track.getDuration());
          waveform.setDurationMs(durationMs);
          newPlayer.playTrack(track);
          latch.countDown();
        }

        @Override
        public void playlistLoaded(AudioPlaylist playlist) {
          AudioTrack first = playlist.getTracks().isEmpty() ? null : playlist.getTracks().getFirst();
          if (first != null) {
            applyWindowStart(first);
            durationMs = Math.max(1L, first.getDuration());
            waveform.setDurationMs(durationMs);
            newPlayer.playTrack(first);
          } else {
            status = PlaybackStatus.ERROR;
          }
          latch.countDown();
        }

        @Override
        public void noMatches() {
          status = PlaybackStatus.ERROR;
          latch.countDown();
        }

        @Override
        public void loadFailed(FriendlyException e) {
          status = PlaybackStatus.ERROR;
          latch.countDown();
        }
      });

      try {
        if (!latch.await(TRACK_LOAD_TIMEOUT_S, TimeUnit.SECONDS)) {
          status = PlaybackStatus.ERROR;
          throw new ResponseStatusException(GATEWAY_TIMEOUT, "Timeout loading track");
        }
      } catch (InterruptedException ie) {
        Thread.currentThread().interrupt();
        status = PlaybackStatus.ERROR;
        throw new ResponseStatusException(INTERNAL_SERVER_ERROR, "Interrupted loading track");
      }

      if (newPlayer.getPlayingTrack() == null) {
        status = PlaybackStatus.ERROR;
        throw notFound("Track could not be loaded");
      }

      startPcmLoop();
    }

    void stop() {
      stopInternal();
      removeThisSession();
    }

    private void stopInternal() {
      cancelExpiry();

      AudioPlayer p = player;
      if (p != null) {
        p.stopTrack();
        p.destroy();
        player = null;
      }

      status = PlaybackStatus.STOPPED;
      trackFinished = false;
      fullyDecoded = false;
      currentTrackId = null;
      cachedStreamToken = null;
      pcmBuffer.clear();
    }

    void setWindow(Long startS, Long endS) {
      this.windowStartS = startS;
      this.windowEndS = endS;
    }

    ResponseEntity<Resource> buildStreamResponse() throws IOException {
      Process ffmpeg = startFfmpeg();

      PipedOutputStream pos = new PipedOutputStream();
      PipedInputStream pis = new PipedInputStream(pos, PIPE_BUFFER_BYTES);

      BlockingQueue<byte[]> listener = pcmBuffer.registerListener();
      List<byte[]> snapshot = pcmBuffer.snapshot();
      boolean completeAtStart = pcmBuffer.isComplete();

      AtomicBoolean cleaned = new AtomicBoolean(false);
      Runnable cleanup = () -> {
        if (!cleaned.compareAndSet(false, true)) return;
        pcmBuffer.unregisterListener(listener);
        destroyQuietly(ffmpeg);
        closeQuietly(pos);
      };

      String prefix = trackMode ? "t" : "b";

      startDaemon("mp3-pump-" + prefix + sessionId, () -> {
        try (InputStream ffOut = ffmpeg.getInputStream();
             OutputStream out = new BufferedOutputStream(pos, 64 * 1024)) {
          ffOut.transferTo(out);
          out.flush();
        } catch (Exception ignored) {
        } finally {
          cleanup.run();
        }
      });

      startDaemon("pcm-feeder-" + prefix + sessionId, () -> {
        try (OutputStream in = new BufferedOutputStream(ffmpeg.getOutputStream(), 64 * 1024)) {
          // Write already decoded history first
          for (byte[] pcm : snapshot) {
            in.write(pcm);
          }
          in.flush();

          if (!completeAtStart) {
            while (!Thread.currentThread().isInterrupted()) {
              byte[] live = listener.poll(300, TimeUnit.MILLISECONDS);

              if (live != null) {
                in.write(live);
                continue;
              }

              if (pcmBuffer.isComplete()) {
                break;
              }

              if (status == PlaybackStatus.ERROR) {
                break;
              }
            }
            in.flush();
          }
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
        } catch (Exception ignored) {
        } finally {
          cleanup.run();
        }
      });

      InputStream closable = new FilterInputStream(pis) {
        @Override
        public void close() throws IOException {
          try {
            super.close();
          } finally {
            cleanup.run();
          }
        }
      };

      return ResponseEntity.ok()
              .contentType(MediaType.parseMediaType("audio/mpeg"))
              .header(HttpHeaders.CACHE_CONTROL, "no-store")
              .header("X-Content-Type-Options", "nosniff")
              .body(new InputStreamResource(closable));
    }

    private void applyWindowStart(AudioTrack track) {
      if (windowStartS == null || windowStartS <= 0) return;
      long startMs = windowStartS * 1000L;
      long dur = track.getDuration();
      if (dur > 0) startMs = Math.min(startMs, Math.max(0, dur - 1000));
      track.setPosition(startMs);
    }

    private void startPcmLoop() {
      long myVersion = streamVersion;

      workers.submit(() -> {
        try {
          while (!Thread.currentThread().isInterrupted()) {
            if (streamVersion != myVersion) break;
            if (status == PlaybackStatus.STOPPED || status == PlaybackStatus.ERROR) break;

            AudioPlayer p = player;
            if (p == null) break;

            AudioTrack t = p.getPlayingTrack();
            if (t == null) {
              AudioFrame leftover;
              while ((leftover = p.provide()) != null) {
                if (streamVersion != myVersion) break;
                byte[] pcm = leftover.getData().clone();
                pcmBuffer.append(pcm);
                waveformWarmup.countDown();
              }
              break;
            }

            if (windowEndS != null && t.getPosition() >= windowEndS * 1000L) {
              p.stopTrack();
              continue;
            }

            AudioFrame frame = p.provide();
            if (frame == null) {
              sleepQuiet(5);
              continue;
            }

            if (streamVersion != myVersion) break;

            byte[] pcm = frame.getData().clone();
            pcmBuffer.append(pcm);
            waveform.accept(pcm, t.getPosition());
            waveformWarmup.countDown();
          }
        } finally {
          status = PlaybackStatus.STOPPED;

          if (trackMode && fullyDecoded && windowStartS == null && windowEndS == null) {
            scheduleExpiry();
          } else if (!trackMode) {
            removeThisSession();
          }
        }
      });
    }

    private void scheduleExpiry() {
      cancelExpiry();
      expiryFuture = scheduler.schedule(this::removeThisSession, TRACK_SESSION_TTL_S, TimeUnit.SECONDS);
    }

    private void cancelExpiry() {
      ScheduledFuture<?> f = expiryFuture;
      if (f != null) {
        f.cancel(false);
        expiryFuture = null;
      }
    }

    private void removeThisSession() {
      stopInternal();
      if (trackMode) {
        trackSessions.values().remove(this);
      } else {
        removeBoardSession(sessionId, this);
      }
    }
  }

  private static Process startFfmpeg() throws IOException {
    ProcessBuilder pb = new ProcessBuilder(
            "ffmpeg", "-hide_banner", "-loglevel", "error",
            "-f", "s16be", "-ar", String.valueOf(PCM_SAMPLE_RATE), "-ac", String.valueOf(PCM_CHANNELS),
            "-i", "pipe:0",
            "-vn", "-map_metadata", "-1", "-codec:a", "libmp3lame",
            "-b:a", "192k", "-write_xing", "0", "-f", "mp3", "pipe:1"
    );
    pb.redirectError(ProcessBuilder.Redirect.INHERIT);
    return pb.start();
  }

  private static void startDaemon(String name, Runnable task) {
    Thread t = new Thread(task, name);
    t.setDaemon(true);
    t.start();
  }

  private static void sleepQuiet(long ms) {
    try {
      Thread.sleep(ms);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private static void destroyQuietly(Process p) {
    try {
      p.destroyForcibly();
    } catch (Exception ignored) {
    }
  }

  private static void closeQuietly(Closeable c) {
    try {
      c.close();
    } catch (Exception ignored) {
    }
  }
}