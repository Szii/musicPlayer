package org.dnd.service.playback;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

public class PcmBroadcastBuffer {

  private final List<byte[]> history = new ArrayList<>();
  private final Set<BlockingQueue<byte[]>> listeners = ConcurrentHashMap.newKeySet();

  @Getter
  private volatile boolean complete = false;

  public synchronized void append(byte[] pcm) {
    byte[] copy = pcm.clone();
    history.add(copy);
    for (BlockingQueue<byte[]> q : listeners) {
      q.offer(copy);
    }
  }

  public synchronized List<byte[]> snapshot() {
    return new ArrayList<>(history);
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
    System.out.println("[pcmBuffer] clear called");
    history.clear();
    complete = false;
    listeners.clear();
  }

  public void markComplete() {
    complete = true;
  }

  public synchronized boolean hasHistory() {
    return !history.isEmpty();
  }
}
