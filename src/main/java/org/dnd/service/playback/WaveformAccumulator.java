package org.dnd.service.playback;

import org.dnd.api.model.WaveformResponse;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class WaveformAccumulator {

  private final int buckets;
  private final float[] peakByBucket;
  private final boolean[] touchedBuckets;

  private volatile long durationMs;

  public WaveformAccumulator(int buckets) {
    this.buckets = buckets;
    this.peakByBucket = new float[buckets];
    this.touchedBuckets = new boolean[buckets];
  }

  public void setDurationMs(long durationMs) {
    this.durationMs = Math.max(1L, durationMs);
  }

  public synchronized void accept(byte[] pcmFrame, long trackPositionMs) {
    if (pcmFrame == null || pcmFrame.length < 2 || durationMs <= 0) {
      return;
    }

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
            Math.max(0L, (trackPositionMs * buckets) / durationMs)
    );

    touchedBuckets[bucket] = true;

    if (max > peakByBucket[bucket]) {
      peakByBucket[bucket] = max;
    }
  }

  public synchronized WaveformResponse toResponse(long trackId, boolean complete) {
    WaveformResponse response = new WaveformResponse();
    response.setTrackId(trackId);
    response.setDurationS(durationMs / 1000L);
    response.setBuckets(buckets);

    List<BigDecimal> peaks = new ArrayList<>(buckets);
    for (float value : peakByBucket) {
      peaks.add(BigDecimal.valueOf(value));
    }

    response.setPeaks(peaks);
    response.setComplete(complete);
    response.setProcessedBuckets(getProcessedBucketsInternal());
    return response;
  }

  public synchronized boolean hasAnyData() {
    for (float value : peakByBucket) {
      if (value > 0f) {
        return true;
      }
    }
    return false;
  }

  public synchronized int getProcessedBuckets() {
    return getProcessedBucketsInternal();
  }

  private int getProcessedBucketsInternal() {
    int count = 0;
    for (boolean touched : touchedBuckets) {
      if (touched) {
        count++;
      }
    }
    return count;
  }
}