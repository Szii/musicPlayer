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
import org.springframework.web.server.ResponseStatusException;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.springframework.http.HttpStatus.*;

@Slf4j
abstract class AbstractAudioDecodeSession {

  protected static final long TRACK_LOAD_TIMEOUT_S = 10;
  protected static final long PCM_PROVIDE_TIMEOUT_MS = 50;
  protected static final long PCM_FALLBACK_IDLE_SLEEP_MS = 15;

  protected final long sessionId;
  protected final AudioPlayerManager playerManager;
  protected final ExecutorService decodeWorkers;
  protected final ScheduledExecutorService scheduler;
  private final Runnable removalCallback;

  protected volatile AudioPlayer player;
  protected volatile long durationMs;
  protected volatile long streamVersion;
  protected volatile boolean trackFinished;
  protected volatile ScheduledFuture<?> cleanupFuture;

  protected AbstractAudioDecodeSession(long sessionId,
                                       AudioPlayerManager playerManager,
                                       ExecutorService decodeWorkers,
                                       ScheduledExecutorService scheduler,
                                       Runnable removalCallback) {
    this.sessionId = sessionId;
    this.playerManager = playerManager;
    this.decodeWorkers = decodeWorkers;
    this.scheduler = scheduler;
    this.removalCallback = removalCallback;
  }

  long getDurationMs() {
    return durationMs;
  }

  protected final void beginPlayback(String trackLink, int trackDurationS, Long windowStartS) {
    AudioPlayer newPlayer = playerManager.createPlayer();
    long playbackVersion = streamVersion + 1;

    this.player = newPlayer;
    this.streamVersion = playbackVersion;
    this.trackFinished = false;
    cancelCleanup();

    String label = sessionLogLabel();

    newPlayer.addListener(new AudioEventAdapter() {
      @Override
      public void onTrackStart(AudioPlayer p, AudioTrack t) {
        if (!isCurrentPlayback(newPlayer, playbackVersion)) {
          return;
        }

        durationMs = Math.max(1L, t.getDuration());
        onTrackStarted(t);

        log.debug("[{}={}] track start: {} ({}ms)",
                label, sessionId, t.getInfo().title, t.getDuration());
      }

      @Override
      public void onTrackEnd(AudioPlayer p, AudioTrack t, AudioTrackEndReason reason) {
        if (!isCurrentPlayback(newPlayer, playbackVersion)) {
          return;
        }

        log.debug("[{}={}] track end: {}", label, sessionId, reason);

        if (reason == AudioTrackEndReason.REPLACED) {
          return;
        }

        trackFinished = true;
      }

      @Override
      public void onTrackStuck(AudioPlayer p, AudioTrack t, long thresholdMs) {
        if (!isCurrentPlayback(newPlayer, playbackVersion)) {
          return;
        }

        log.warn("[{}={}] track stuck ({}ms)", label, sessionId, thresholdMs);
        onPlaybackFailure();
      }

      @Override
      public void onTrackException(AudioPlayer p, AudioTrack t, FriendlyException ex) {
        if (!isCurrentPlayback(newPlayer, playbackVersion)) {
          return;
        }

        log.error("[{}={}] track exception: {}", label, sessionId, ex.getMessage(), ex);
        onPlaybackFailure();
      }
    });

    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<ResponseStatusException> loadFailure = new AtomicReference<>();

    playerManager.loadItem(trackLink, new AudioLoadResultHandler() {
      @Override
      public void trackLoaded(AudioTrack track) {
        if (!isCurrentPlayback(newPlayer, playbackVersion)) {
          latch.countDown();
          return;
        }

        applyWindowStart(track, trackDurationS, windowStartS);
        durationMs = Math.max(1L, track.getDuration());
        onTrackPrepared(track);
        newPlayer.playTrack(track);
        latch.countDown();
      }

      @Override
      public void playlistLoaded(AudioPlaylist playlist) {
        if (!isCurrentPlayback(newPlayer, playbackVersion)) {
          latch.countDown();
          return;
        }

        AudioTrack first = playlist.getTracks().isEmpty() ? null : playlist.getTracks().getFirst();
        if (first == null) {
          loadFailure.set(new ResponseStatusException(NOT_FOUND, "Playlist was empty"));
          latch.countDown();
          return;
        }

        applyWindowStart(first, trackDurationS, windowStartS);
        durationMs = Math.max(1L, first.getDuration());
        onTrackPrepared(first);
        newPlayer.playTrack(first);
        latch.countDown();
      }

      @Override
      public void noMatches() {
        loadFailure.set(new ResponseStatusException(NOT_FOUND, "Track could not be loaded"));
        latch.countDown();
      }

      @Override
      public void loadFailed(FriendlyException e) {
        loadFailure.set(new ResponseStatusException(BAD_GATEWAY, "Track loading failed"));
        latch.countDown();
      }
    });

    try {
      if (!latch.await(TRACK_LOAD_TIMEOUT_S, TimeUnit.SECONDS)) {
        throw new ResponseStatusException(GATEWAY_TIMEOUT, "Timeout loading track");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ResponseStatusException(INTERNAL_SERVER_ERROR, "Interrupted loading track");
    }

    ResponseStatusException failure = loadFailure.get();
    if (failure != null) {
      destroyQuietly(newPlayer);
      throw failure;
    }

    if (!isCurrentPlayback(newPlayer, playbackVersion) || newPlayer.getPlayingTrack() == null) {
      destroyQuietly(newPlayer);
      throw new ResponseStatusException(NOT_FOUND, "Track could not be loaded");
    }

    startDecodeLoop(playbackVersion, newPlayer);
  }

  protected final void startDecodeLoop(long playbackVersion, AudioPlayer playbackPlayer) {
    decodeWorkers.submit(() -> {
      boolean drainedToNaturalEnd = false;

      try {
        while (!Thread.currentThread().isInterrupted()) {
          if (!isCurrentPlayback(playbackPlayer, playbackVersion)) {
            break;
          }

          AudioFrame frame = provideFrame(playbackPlayer);
          if (frame != null) {
            if (!isCurrentPlayback(playbackPlayer, playbackVersion)) {
              break;
            }

            AudioTrack currentTrack = playbackPlayer.getPlayingTrack();
            Long positionMs = currentTrack != null ? Math.max(0L, currentTrack.getPosition()) : null;
            onPcmFrame(frame.getData(), positionMs);
            continue;
          }

          if (trackFinished && isCurrentPlayback(playbackPlayer, playbackVersion)) {
            drainAvailableFrames(playbackPlayer, playbackVersion);
            drainedToNaturalEnd = true;
            break;
          }
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      } finally {
        if (!isCurrentPlayback(playbackPlayer, playbackVersion)) {
          return;
        }

        if (drainedToNaturalEnd) {
          onPlaybackCompleted(playbackPlayer, playbackVersion);
        }
      }
    });
  }

  private AudioFrame provideFrame(AudioPlayer playbackPlayer) {
    try {
      return playbackPlayer.provide(PCM_PROVIDE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    } catch (TimeoutException e) {
      return null;
    } catch (UnsupportedOperationException e) {
      AudioFrame frame = playbackPlayer.provide();
      if (frame == null) {
        sleepQuiet(PCM_FALLBACK_IDLE_SLEEP_MS);
      }
      return frame;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return null;
    }
  }

  private void drainAvailableFrames(AudioPlayer playbackPlayer, long playbackVersion) throws InterruptedException {
    AudioFrame leftover;
    while ((leftover = playbackPlayer.provide()) != null) {
      if (!isCurrentPlayback(playbackPlayer, playbackVersion)) {
        break;
      }
      onPcmFrame(leftover.getData(), null);
    }
  }

  protected final void scheduleCleanup(long ttlSeconds) {
    cancelCleanup();
    cleanupFuture = scheduler.schedule(this::removeThisSession, ttlSeconds, TimeUnit.SECONDS);
  }

  protected final void cancelCleanup() {
    ScheduledFuture<?> future = cleanupFuture;
    if (future != null) {
      future.cancel(false);
      cleanupFuture = null;
    }
  }

  protected final void destroyPlayerOnly(AudioPlayer candidate, long playbackVersion) {
    if (!isCurrentPlayback(candidate, playbackVersion)) {
      destroyQuietly(candidate);
      return;
    }

    destroyQuietly(candidate);

    if (player == candidate) {
      player = null;
    }

    trackFinished = false;
  }

  protected final void stopInternal() {
    cancelCleanup();

    AudioPlayer currentPlayer = player;
    if (currentPlayer != null) {
      destroyQuietly(currentPlayer);
      player = null;
    }

    durationMs = 0L;
    trackFinished = false;
    clearSubclassState();
  }

  protected final void removeThisSession() {
    stopInternal();
    removalCallback.run();
  }

  protected final boolean isCurrentPlayback(AudioPlayer candidate, long playbackVersion) {
    return player == candidate && streamVersion == playbackVersion;
  }

  protected void onTrackPrepared(AudioTrack track) {
    // optional hook
  }

  protected void onTrackStarted(AudioTrack track) {
    // optional hook
  }

  protected void onPlaybackFailure() {
    removeThisSession();
  }

  protected abstract String sessionLogLabel();

  protected abstract void onPcmFrame(byte[] pcm, Long positionMs) throws InterruptedException;

  protected abstract void onPlaybackCompleted(AudioPlayer playbackPlayer, long playbackVersion);

  protected abstract void clearSubclassState();

  private static void applyWindowStart(AudioTrack track, int trackDurationS, Long windowStartS) {
    if (windowStartS == null || windowStartS <= 0) {
      return;
    }

    long startMs = windowStartS * 1000L;
    long duration = track.getDuration() > 0 ? track.getDuration() : (long) trackDurationS * 1000L;

    if (duration > 0) {
      startMs = Math.min(startMs, Math.max(0L, duration - 1000L));
    }

    track.setPosition(startMs);
  }

  protected static void sleepQuiet(long ms) {
    try {
      Thread.sleep(ms);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  protected static void destroyQuietly(AudioPlayer audioPlayer) {
    try {
      audioPlayer.stopTrack();
    } catch (Exception ignored) {
    }

    try {
      audioPlayer.destroy();
    } catch (Exception ignored) {
    }
  }
}