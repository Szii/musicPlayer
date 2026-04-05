package org.dnd.service.playback;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import com.sedmelluq.discord.lavaplayer.track.playback.AudioFrame;
import lombok.extern.slf4j.Slf4j;
import org.dnd.api.model.PlaybackState;
import org.dnd.api.model.PlaybackStatus;
import org.dnd.api.model.WaveformResponse;
import org.dnd.service.JwtService;
import org.dnd.utils.SecurityUtils;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.io.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static org.springframework.http.HttpStatus.*;

@Slf4j
final public class StreamSession {

  private static final int PCM_SAMPLE_RATE = 48000;
  private static final int PCM_CHANNELS = 2;
  private static final int PIPE_BUFFER_BYTES = 256 * 1024;
  private static final long TRACK_LOAD_TIMEOUT_S = 10;
  private static final int WAVEFORM_BUCKETS = 512;
  private static final long TRACK_SESSION_TTL_S = 60;

  private final long sessionId;
  private final boolean trackMode;
  private final AudioPlayerManager playerManager;
  private final JwtService jwtService;
  private final ExecutorService workers;
  private final ScheduledExecutorService scheduler;
  private final Consumer<StreamSession> removalCallback;

  private volatile AudioPlayer player;
  volatile PlaybackStatus status = PlaybackStatus.STOPPED;

  private volatile Long currentTrackId;
  private volatile Long windowStartS;
  private volatile Long windowEndS;
  volatile long durationMs;

  private volatile long streamVersion;
  private volatile boolean trackFinished;
  private volatile boolean fullyDecoded;

  private final PcmBroadcastBuffer pcmBuffer;
  private final WaveformAccumulator waveform = new WaveformAccumulator(WAVEFORM_BUCKETS);
  private final CountDownLatch waveformWarmup = new CountDownLatch(1);

  private volatile String cachedStreamToken;
  private volatile long cachedTokenUserId = -1;

  private volatile ScheduledFuture<?> expiryFuture;

  StreamSession(long sessionId,
                boolean trackMode,
                AudioPlayerManager playerManager,
                JwtService jwtService,
                ExecutorService workers,
                ScheduledExecutorService scheduler,
                Consumer<StreamSession> removalCallback) {
    this.sessionId = sessionId;
    this.trackMode = trackMode;
    this.playerManager = playerManager;
    this.jwtService = jwtService;
    this.workers = workers;
    this.scheduler = scheduler;
    this.removalCallback = removalCallback;
    this.pcmBuffer = new PcmBroadcastBuffer(trackMode);
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

  WaveformResponse getWaveformResponse() {
    return waveform.toResponse(sessionId, fullyDecoded);
  }

  long getDurationMs() {
    return durationMs;
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

      if (cachedStreamToken == null || cachedTokenUserId != userId) {
        cachedStreamToken = trackMode
                ? jwtService.generateTrackStreamToken(userId, sessionId)
                : jwtService.generateStreamToken(userId, sessionId);
        cachedTokenUserId = userId;
      }

      if (trackMode) {
        ps.setStreamUrl("/tracks/" + sessionId + "/stream?streamToken=" + cachedStreamToken);
      } else {
        ps.setStreamUrl("/boards/" + sessionId + "/stream?streamToken=" + cachedStreamToken);
      }
    }

    return ps;
  }

  void loadAndPlay(long trackId, String trackLink, int trackDuration) {
    stopInternal();

    AudioPlayer newPlayer = playerManager.createPlayer();
    this.player = newPlayer;

    status = PlaybackStatus.PLAYING;
    currentTrackId = trackId;
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
      public void onTrackStart(AudioPlayer p, AudioTrack t) {
        status = PlaybackStatus.PLAYING;
        durationMs = Math.max(1L, t.getDuration());
        waveform.setDurationMs(durationMs);
        log.debug("[{}={}] track start: {} ({}ms)", label, sessionId, t.getInfo().title, t.getDuration());
      }

      @Override
      public void onTrackEnd(AudioPlayer p, AudioTrack t, AudioTrackEndReason reason) {
        log.debug("[{}={}] track end: {}", label, sessionId, reason);
        if (reason == AudioTrackEndReason.REPLACED) return;
        trackFinished = true;
        fullyDecoded = true;
      }

      @Override
      public void onTrackStuck(AudioPlayer p, AudioTrack t, long thresholdMs) {
        log.warn("[{}={}] track stuck ({}ms)", label, sessionId, thresholdMs);
        status = PlaybackStatus.ERROR;
        removeThisSession();
      }

      @Override
      public void onTrackException(AudioPlayer p, AudioTrack t, FriendlyException ex) {
        log.error("[{}={}] track exception: {}", label, sessionId, ex.getMessage(), ex);
        status = PlaybackStatus.ERROR;
        removeThisSession();
      }
    });

    CountDownLatch latch = new CountDownLatch(1);

    playerManager.loadItem(trackLink, new AudioLoadResultHandler() {
      @Override
      public void trackLoaded(AudioTrack t) {
        applyWindowStart(t, trackDuration);
        durationMs = Math.max(1L, t.getDuration());
        waveform.setDurationMs(durationMs);
        newPlayer.playTrack(t);
        latch.countDown();
      }

      @Override
      public void playlistLoaded(AudioPlaylist playlist) {
        AudioTrack first = playlist.getTracks().isEmpty() ? null : playlist.getTracks().getFirst();
        if (first != null) {
          applyWindowStart(first, trackDuration);
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
      throw new ResponseStatusException(NOT_FOUND, "Track could not be loaded");
    }

    startPcmLoop();
  }

  void stop() {
    removeThisSession();
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
      boolean normalExit = false;
      try (OutputStream in = new BufferedOutputStream(ffmpeg.getOutputStream(), 64 * 1024)) {
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
              byte[] tail;
              while ((tail = listener.poll()) != null) {
                in.write(tail);
              }
              normalExit = true;
              break;
            }

            if (status == PlaybackStatus.ERROR) break;
          }
          in.flush();
        } else {
          normalExit = true;
        }
      } catch (InterruptedException ie) {
        Thread.currentThread().interrupt();
      } catch (Exception ignored) {
      } finally {
        if (!normalExit) cleanup.run();
        pcmBuffer.unregisterListener(listener);
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

  private void applyWindowStart(AudioTrack track, int trackDurationS) {
    if (windowStartS == null || windowStartS <= 0) return;
    long startMs = windowStartS * 1000L;
    long dur = track.getDuration() > 0 ? track.getDuration() : (long) trackDurationS * 1000;
    if (dur > 0) startMs = Math.min(startMs, Math.max(0, dur - 1000));
    track.setPosition(startMs);
  }

  private void startPcmLoop() {
    long myVersion = streamVersion;

    workers.submit(() -> {
      boolean drainedToNaturalEnd = false;
      boolean failed;

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

            if (trackFinished) {
              drainedToNaturalEnd = true;
              break;
            }

            sleepQuiet(5);
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
        failed = status == PlaybackStatus.ERROR;

        log.debug("[session={}] pcm loop exit — drainedToNaturalEnd={}, fullyDecoded={}, failed={}",
                sessionId, drainedToNaturalEnd, fullyDecoded, failed);

        if (drainedToNaturalEnd) {
          fullyDecoded = true;
          pcmBuffer.markComplete();
        }

        if (!failed) {
          status = PlaybackStatus.STOPPED;
        }

        if (fullyDecoded) {
          scheduleExpiry();
        } else if (failed) {
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
    log.debug("[session={}] removeThisSession", sessionId);
    stopInternal();
    removalCallback.accept(this);
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