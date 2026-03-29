package org.dnd.service.playback;

import lombok.Getter;

import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

public class PcmBroadcastBuffer {

  /**
   * Board sessions (storeHistory=false):
   * Keep a small rolling buffer — enough for a new HTTP connection to get a
   * quick start without missing the very beginning of a window, but bounded
   * so long tracks don't accumulate unbounded memory.
   * ~3 seconds at 192 kbps ≈ 150 PCM frames (each ~3840 bytes at 20ms/frame).
   * <p>
   * Track sessions (storeHistory=true):
   * Keep the full history so the window editor can replay/seek anywhere.
   */
  private final boolean storeHistory;
  private static final int ROLLING_BUFFER_MAX_FRAMES = 150; // ~3 seconds

  private final Deque<byte[]> history = new ArrayDeque<>();
  private final Set<BlockingQueue<byte[]>> listeners = ConcurrentHashMap.newKeySet();

  @Getter
  private volatile boolean complete = false;

  public PcmBroadcastBuffer(boolean storeHistory) {
    this.storeHistory = storeHistory;
  }

  public synchronized void append(byte[] pcm) {
    byte[] copy = pcm.clone();

    if (storeHistory) {
      // Track sessions: keep everything
      history.addLast(copy);
    } else {
      // Board sessions: rolling buffer — evict oldest when full
      history.addLast(copy);
      while (history.size() > ROLLING_BUFFER_MAX_FRAMES) {
        history.removeFirst();
      }
    }

    for (BlockingQueue<byte[]> q : listeners) {
      q.offer(copy);
    }
  }

  /**
   * Returns a snapshot of current history.
   * For board sessions this is the last ~3 seconds (rolling window).
   * For track sessions this is the full decoded history.
   */
  public synchronized List<byte[]> snapshot() {
    return new ArrayList<>(history);
  }

  public synchronized boolean hasHistory() {
    return !history.isEmpty();
  }

  public BlockingQueue<byte[]> registerListener() {
    BlockingQueue<byte[]> q = new LinkedBlockingQueue<>();
    listeners.add(q);
    return q;
  }

  public void unregisterListener(BlockingQueue<byte[]> q) {
    listeners.remove(q);
  }

  public synchronized void clear() {
    history.clear();
    complete = false;
    listeners.clear();
  }

  public void markComplete() {
    complete = true;
  }
}