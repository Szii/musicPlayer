package org.dnd.service.playback;

import org.dnd.api.model.WaveformResponse;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class WaveformAccumulator {

  private final int buckets;
  private final float[] peakByBucket;
  private volatile long durationMs;

  public WaveformAccumulator(int buckets) {
    this.buckets = buckets;
    this.peakByBucket = new float[buckets];
  }

  public void setDurationMs(long durationMs) {
    this.durationMs = Math.max(1, durationMs);
  }

  /**
   * Accept a PCM frame in signed 16-bit big-endian stereo format.
   * We map the frame to a bucket using the current track position.
   */
  public void accept(byte[] pcmFrame, long trackPositionMs) {
    if (pcmFrame == null || pcmFrame.length < 2 || durationMs <= 0) return;

    float max = 0f;

    for (int i = 0; i + 1 < pcmFrame.length; i += 2) {
      int hi = pcmFrame[i] & 0xFF;
      int lo = pcmFrame[i + 1] & 0xFF;
      short sample = (short) ((hi << 8) | lo);
      float normalized = Math.abs(sample) / 32768f;
      if (normalized > max) {
        max = normalized;
      }
    }

    int bucket = (int) Math.min(
            buckets - 1,
            Math.max(0, (trackPositionMs * buckets) / durationMs)
    );

    if (max > peakByBucket[bucket]) {
      peakByBucket[bucket] = max;
    }
  }

  public boolean hasAnyData() {
    for (float v : peakByBucket) {
      if (v > 0f) return true;
    }
    return false;
  }

  public WaveformResponse toResponse(long trackId) {
    WaveformResponse response = new WaveformResponse();
    response.setTrackId(trackId);
    response.setDurationS(durationMs / 1000L);
    response.setBuckets(buckets);

    List<BigDecimal> peaks = new ArrayList<>(buckets);
    for (float v : peakByBucket) {
      peaks.add(BigDecimal.valueOf(v));
    }
    response.setPeaks(peaks);
    return response;
  }
}
