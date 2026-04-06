package org.dnd.service.playback;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import org.dnd.api.model.PlaybackState;
import org.dnd.api.model.PlaybackStatus;
import org.dnd.service.JwtService;
import org.dnd.utils.SecurityUtils;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.io.*;
import java.util.List;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public final class StreamSession extends AbstractAudioDecodeSession {

  private static final int PCM_SAMPLE_RATE = 48000;
  private static final int PCM_CHANNELS = 2;
  private static final int PIPE_BUFFER_BYTES = 256 * 1024;
  private static final int STREAM_IO_BUFFER_BYTES = 64 * 1024;
  private static final long COMPLETED_SESSION_TTL_S = 10;
  private static final long LISTENER_POLL_TIMEOUT_MS = 100;

  private final boolean trackMode;
  private final JwtService jwtService;
  private final ExecutorService streamIoWorkers;
  private final Consumer<StreamSession> removalCallback;
  private final PcmBroadcastBuffer pcmBuffer = new PcmBroadcastBuffer();
  private final AtomicReference<ActiveStream> activeStreamRef = new AtomicReference<>();

  volatile PlaybackStatus status = PlaybackStatus.STOPPED;
  private volatile Long currentTrackId;
  private volatile Long windowStartS;
  private volatile Long windowEndS;
  private volatile String cachedStreamToken;
  private volatile long cachedTokenUserId = -1;

  StreamSession(long sessionId,
                boolean trackMode,
                AudioPlayerManager playerManager,
                JwtService jwtService,
                ExecutorService decodeWorkers,
                ExecutorService streamIoWorkers,
                ScheduledExecutorService scheduler,
                Consumer<StreamSession> removalCallback) {
    super(sessionId, playerManager, decodeWorkers, scheduler);
    this.trackMode = trackMode;
    this.jwtService = jwtService;
    this.streamIoWorkers = streamIoWorkers;
    this.removalCallback = removalCallback;
  }

  boolean canServeStream() {
    return status == PlaybackStatus.PLAYING;
  }

  PlaybackState snapshot() {
    PlaybackState state = new PlaybackState();
    state.setStatus(status);
    state.setTrackId(currentTrackId);
    state.setWindowStartS(windowStartS);
    state.setWindowEndS(windowEndS);

    if (trackMode) {
      state.setBoardId(null);
    } else {
      state.setBoardId(sessionId);
    }

    AudioPlayer currentPlayer = player;
    AudioTrack track = currentPlayer != null ? currentPlayer.getPlayingTrack() : null;
    state.setPositionS(track != null ? Math.max(0L, track.getPosition() / 1000L) : null);

    if (canServeStream()) {
      long userId = SecurityUtils.getCurrentUserId();

      if (cachedStreamToken == null || cachedTokenUserId != userId) {
        cachedStreamToken = trackMode
                ? jwtService.generateTrackStreamToken(userId, sessionId)
                : jwtService.generateStreamToken(userId, sessionId);
        cachedTokenUserId = userId;
      }

      if (trackMode) {
        state.setStreamUrl("/tracks/" + sessionId + "/stream?streamToken=" + cachedStreamToken);
      } else {
        state.setStreamUrl("/boards/" + sessionId + "/stream?streamToken=" + cachedStreamToken);
      }
    }

    return state;
  }

  void loadAndPlay(long trackId, String trackLink, int trackDuration, Long windowStartS, Long windowEndS) {
    stopInternal();

    this.windowStartS = windowStartS;
    this.windowEndS = windowEndS;
    this.status = PlaybackStatus.PLAYING;
    this.currentTrackId = trackId;
    this.cachedStreamToken = null;
    this.cachedTokenUserId = -1;

    beginPlayback(trackLink, trackDuration, this.windowStartS);
  }

  void stop() {
    removeThisSession();
  }

  ResponseEntity<Resource> buildStreamResponse() throws IOException {
    Process ffmpeg = startFfmpeg();

    PipedOutputStream pos = new PipedOutputStream();
    PipedInputStream pis = new PipedInputStream(pos, PIPE_BUFFER_BYTES);

    BlockingDeque<byte[]> listener = pcmBuffer.registerListener();
    List<byte[]> snapshot = pcmBuffer.snapshot();
    boolean completeAtStart = pcmBuffer.isComplete();

    AtomicBoolean cleaned = new AtomicBoolean(false);
    ActiveStream[] selfHolder = new ActiveStream[1];

    Runnable cleanup = () -> {
      if (!cleaned.compareAndSet(false, true)) {
        return;
      }

      pcmBuffer.unregisterListener(listener);
      destroyQuietly(ffmpeg);
      closeQuietly(pos);

      ActiveStream self = selfHolder[0];
      if (self != null) {
        activeStreamRef.compareAndSet(self, null);
      }
    };

    ActiveStream activeStream = new ActiveStream(cleanup);
    selfHolder[0] = activeStream;

    ActiveStream previousActive = activeStreamRef.getAndSet(activeStream);
    if (previousActive != null) {
      previousActive.close();
    }

    startAsync(() -> {
      try (InputStream ffOut = ffmpeg.getInputStream();
           OutputStream out = new BufferedOutputStream(pos, STREAM_IO_BUFFER_BYTES)) {
        ffOut.transferTo(out);
        out.flush();
      } catch (Exception ignored) {
      } finally {
        activeStream.close();
      }
    });

    startAsync(() -> {
      boolean normalExit = false;

      try (OutputStream in = new BufferedOutputStream(ffmpeg.getOutputStream(), STREAM_IO_BUFFER_BYTES)) {
        for (byte[] pcm : snapshot) {
          in.write(pcm);
        }
        in.flush();

        if (completeAtStart) {
          normalExit = true;
          return;
        }

        while (!Thread.currentThread().isInterrupted()) {
          byte[] live = listener.pollFirst(LISTENER_POLL_TIMEOUT_MS, TimeUnit.MILLISECONDS);

          if (live != null) {
            in.write(live);
            continue;
          }

          if (pcmBuffer.isComplete()) {
            byte[] tail;
            while ((tail = listener.pollFirst()) != null) {
              in.write(tail);
            }
            in.flush();
            normalExit = true;
            break;
          }

          if (status == PlaybackStatus.ERROR || status == PlaybackStatus.STOPPED) {
            break;
          }
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      } catch (Exception ignored) {
      } finally {
        if (!normalExit) {
          activeStream.close();
        }
      }
    });

    InputStream closable = new FilterInputStream(pis) {
      @Override
      public void close() throws IOException {
        try {
          super.close();
        } finally {
          activeStream.close();
        }
      }
    };

    return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("audio/mpeg"))
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .header("X-Content-Type-Options", "nosniff")
            .body(new InputStreamResource(closable));
  }

  @Override
  protected String sessionLogLabel() {
    return trackMode ? "track" : "board";
  }

  @Override
  protected void onPcmFrame(byte[] pcm, Long positionMs) throws InterruptedException {
    pcmBuffer.append(pcm);
  }

  @Override
  protected void onPlaybackCompleted(AudioPlayer playbackPlayer, long playbackVersion) {
    pcmBuffer.markComplete();
    status = PlaybackStatus.STOPPED;
    destroyPlayerOnly(playbackPlayer, playbackVersion);
    scheduleCleanup(COMPLETED_SESSION_TTL_S);
  }

  @Override
  protected void onPlaybackFailure() {
    status = PlaybackStatus.ERROR;
    super.onPlaybackFailure();
  }

  @Override
  protected void clearSubclassState() {
    status = PlaybackStatus.STOPPED;
    currentTrackId = null;
    windowStartS = null;
    windowEndS = null;
    cachedStreamToken = null;
    cachedTokenUserId = -1;

    ActiveStream active = activeStreamRef.getAndSet(null);
    if (active != null) {
      active.close();
    }

    pcmBuffer.clear();
  }

  @Override
  protected void removeFromManager() {
    removalCallback.accept(this);
  }

  private Process startFfmpeg() throws IOException {
    ProcessBuilder processBuilder = new ProcessBuilder(
            "ffmpeg", "-hide_banner", "-loglevel", "error",
            "-f", "s16be", "-ar", String.valueOf(PCM_SAMPLE_RATE), "-ac", String.valueOf(PCM_CHANNELS),
            "-i", "pipe:0",
            "-vn", "-map_metadata", "-1", "-codec:a", "libmp3lame",
            "-b:a", "192k", "-write_xing", "0", "-f", "mp3", "pipe:1"
    );
    processBuilder.redirectError(ProcessBuilder.Redirect.INHERIT);
    return processBuilder.start();
  }

  private void startAsync(Runnable task) {
    streamIoWorkers.submit(task);
  }

  private static void destroyQuietly(Process process) {
    try {
      process.destroyForcibly();
    } catch (Exception ignored) {
    }
  }

  private static void closeQuietly(Closeable closeable) {
    try {
      closeable.close();
    } catch (Exception ignored) {
    }
  }

  private static final class ActiveStream {
    private final Runnable cleanup;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private ActiveStream(Runnable cleanup) {
      this.cleanup = cleanup;
    }

    void close() {
      if (closed.compareAndSet(false, true)) {
        cleanup.run();
      }
    }
  }
}