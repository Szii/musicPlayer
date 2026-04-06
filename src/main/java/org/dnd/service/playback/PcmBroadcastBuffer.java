package org.dnd.service.playback;

import lombok.Getter;

import java.util.*;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;

public class PcmBroadcastBuffer {

  /**
   * Short rolling history for newly attached streams.
   * Does not affect live correctness.
   */
  private static final int ROLLING_BUFFER_MAX_FRAMES = 500;   // ~10s at ~20ms/frame

  /**
   * Bounded live queue per listener.
   * When full, producer blocks instead of dropping audio.
   */
  private static final int LISTENER_QUEUE_MAX_FRAMES = 500;   // ~10s of PCM backlog

  private final Deque<byte[]> history = new ArrayDeque<>();
  private final Set<BlockingDeque<byte[]>> listeners = ConcurrentHashMap.newKeySet();

  @Getter
  private volatile boolean complete = false;

  public void append(byte[] pcm) throws InterruptedException {
    byte[] copy = pcm.clone();
    List<BlockingDeque<byte[]>> listenerSnapshot;

    synchronized (this) {
      history.addLast(copy);
      while (history.size() > ROLLING_BUFFER_MAX_FRAMES) {
        history.removeFirst();
      }

      listenerSnapshot = new ArrayList<>(listeners);
    }

    for (BlockingDeque<byte[]> queue : listenerSnapshot) {
      queue.putLast(copy);
    }
  }

  public synchronized List<byte[]> snapshot() {
    return new ArrayList<>(history);
  }

  public BlockingDeque<byte[]> registerListener() {
    BlockingDeque<byte[]> queue = new LinkedBlockingDeque<>(LISTENER_QUEUE_MAX_FRAMES);
    listeners.add(queue);
    return queue;
  }

  public void unregisterListener(BlockingDeque<byte[]> queue) {
    listeners.remove(queue);
    queue.clear();
  }

  public synchronized void clear() {
    history.clear();
    complete = false;

    for (BlockingDeque<byte[]> queue : listeners) {
      queue.clear();
    }
    listeners.clear();
  }

  public void markComplete() {
    complete = true;
  }
}