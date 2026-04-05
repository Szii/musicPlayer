package org.dnd.service.playback;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import lombok.extern.slf4j.Slf4j;
import org.dnd.api.model.WaveformResponse;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;

@Slf4j
public final class WaveformSession extends AbstractAudioDecodeSession {

  private static final int PCM_SAMPLE_RATE = 48000;
  private static final int PCM_CHANNELS = 2;
  private static final int PCM_BYTES_PER_SAMPLE = 2;
  private static final long WAVEFORM_CACHE_TTL_S = 60;
  private static final int WAVEFORM_BUCKETS = 512;

  private final Consumer<WaveformSession> removalCallback;

  private volatile WaveformAccumulator waveform = new WaveformAccumulator(WAVEFORM_BUCKETS);
  private volatile boolean complete;
  private volatile long lastWaveformPositionMs;

  WaveformSession(long trackId,
                  AudioPlayerManager playerManager,
                  ExecutorService decodeWorkers,
                  ScheduledExecutorService scheduler,
                  Consumer<WaveformSession> removalCallback) {
    super(trackId, playerManager, decodeWorkers, scheduler);
    this.removalCallback = removalCallback;
  }

  WaveformResponse getWaveformResponse() {
    return waveform.toResponse(sessionId, complete);
  }

  void loadAndAnalyze(long requestedTrackId, String trackLink, int trackDuration) {
    stopInternal();

    this.waveform = new WaveformAccumulator(WAVEFORM_BUCKETS);
    this.complete = false;
    this.lastWaveformPositionMs = 0L;
    this.durationMs = Math.max(1L, trackDuration * 1000L);
    this.waveform.setDurationMs(durationMs);

    beginPlayback(trackLink, trackDuration, null);
  }

  void stop() {
    removeThisSession();
  }

  @Override
  protected String sessionLogLabel() {
    return "waveform";
  }

  @Override
  protected void onTrackPrepared(AudioTrack track) {
    waveform.setDurationMs(durationMs);
  }

  @Override
  protected void onTrackStarted(AudioTrack track) {
    waveform.setDurationMs(durationMs);
  }

  @Override
  protected void onPcmFrame(byte[] pcm, Long positionMs) {
    long effectivePosition = positionMs != null ? positionMs : lastWaveformPositionMs;
    waveform.accept(pcm, effectivePosition);

    long nextPosition = effectivePosition + estimateFrameDurationMs(pcm);
    if (nextPosition > lastWaveformPositionMs) {
      lastWaveformPositionMs = nextPosition;
    }
  }

  @Override
  protected void onPlaybackCompleted(AudioPlayer playbackPlayer, long playbackVersion) {
    complete = true;
    destroyPlayerOnly(playbackPlayer, playbackVersion);
    scheduleCleanup(WAVEFORM_CACHE_TTL_S);
  }

  @Override
  protected void clearSubclassState() {
    complete = false;
    lastWaveformPositionMs = 0L;
  }

  @Override
  protected void removeFromManager() {
    removalCallback.accept(this);
  }

  private static long estimateFrameDurationMs(byte[] pcm) {
    long bytesPerMs = (long) PCM_SAMPLE_RATE * PCM_CHANNELS * PCM_BYTES_PER_SAMPLE / 1000L;
    if (bytesPerMs <= 0) {
      return 1L;
    }
    return Math.max(1L, pcm.length / bytesPerMs);
  }
}