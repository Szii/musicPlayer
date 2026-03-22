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
import org.dnd.api.model.PlayRequest;
import org.dnd.api.model.PlaybackState;
import org.dnd.api.model.PlaybackStatus;
import org.dnd.api.model.SeekRequest;
import org.dnd.model.BoardEntity;
import org.dnd.model.TrackEntity;
import org.dnd.repository.BoardRepository;
import org.dnd.repository.TrackWindowRepository;
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
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.springframework.http.HttpStatus.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlaybackService {

    private static final int PCM_SAMPLE_RATE = 48000;
    private static final int PCM_CHANNELS = 2;
    private static final int PCM_QUEUE_CAPACITY = 512;
    private static final int PIPE_BUFFER_BYTES = 256 * 1024;
    private static final long TRACK_LOAD_TIMEOUT_S = 10;

    private final BoardRepository boardRepository;
    private final TrackWindowRepository trackWindowRepository;
    private final AudioPlayerManager playerManager;
    private final JwtService jwtService;

    private final ConcurrentMap<Long, BoardSession> sessions = new ConcurrentHashMap<>();
    private ExecutorService workers;

    @PostConstruct
    private void init() {
        playerManager.getConfiguration().setOutputFormat(StandardAudioDataFormats.DISCORD_PCM_S16_BE);
        workers = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "board-worker");
            t.setDaemon(true);
            return t;
        });
    }

    @PreDestroy
    private void shutdown() {
        sessions.values().forEach(BoardSession::stop);
        sessions.clear();
        workers.shutdownNow();
    }

    public PlaybackState getState(long boardId) {
        requireOwnedBoard(boardId);
        BoardSession s = sessions.get(boardId);
        return s != null ? s.snapshot() : stoppedState(boardId);
    }

    public PlaybackState play(long boardId, PlayRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        BoardEntity board = requireOwnedBoard(boardId);

        TrackEntity track = board.getSelectedTrack();
        if (track == null) throw conflict("No track selected on board");
        if (!track.getOwner().getId().equals(userId)) throw forbidden("Track not accessible");

        Long windowId = request != null ? request.getWindowId() : null;
        Long windowStartS = null, windowEndS = null;
        if (windowId != null) {
            var window = trackWindowRepository.findById(windowId)
                    .orElseThrow(() -> notFound("Track window not found"));
            windowStartS = window.getPositionFrom();
            windowEndS = window.getPositionTo();
        }

        BoardSession existing = sessions.remove(boardId);
        if (existing != null) existing.stop();

        BoardSession s = new BoardSession(boardId);
        sessions.put(boardId, s);
        s.setWindow(windowStartS, windowEndS);
        s.loadAndPlay(track);
        return s.snapshot();
    }

    public PlaybackState stop(long boardId) {
        requireOwnedBoard(boardId);
        BoardSession s = sessions.remove(boardId);
        if (s == null) return stoppedState(boardId);
        s.stop();
        return stoppedState(boardId);
    }

    @Deprecated
    public PlaybackState pause(long boardId) {
        requireOwnedBoard(boardId);
        BoardSession s = sessions.get(boardId);
        return s != null ? s.snapshot() : stoppedState(boardId);
    }

    @Deprecated
    public PlaybackState resume(long boardId) {
        requireOwnedBoard(boardId);
        BoardSession s = sessions.get(boardId);
        return s != null ? s.snapshot() : stoppedState(boardId);
    }

    @Deprecated
    public PlaybackState seek(long boardId, SeekRequest req) {
        requireOwnedBoard(boardId);
        BoardSession s = sessions.get(boardId);
        return s != null ? s.snapshot() : stoppedState(boardId);
    }

    public ResponseEntity<Resource> streamMp3ForUser(long boardId, long userId) {
        requireOwnedBoardByUserId(boardId, userId);
        BoardSession s = sessions.get(boardId);
        if (s == null || s.status != PlaybackStatus.PLAYING) throw conflict("Board is not playing");

        try {
            return s.buildStreamResponse();
        } catch (IOException e) {
            log.error("[board={}] Failed to start stream", boardId, e);
            throw new ResponseStatusException(INTERNAL_SERVER_ERROR, "Streaming failed");
        }
    }

    private BoardEntity requireOwnedBoard(long boardId) {
        Long userId = SecurityUtils.getCurrentUserId();
        BoardEntity board = boardRepository.findById(boardId)
                .orElseThrow(() -> notFound("Board not found"));
        if (!board.getOwner().getId().equals(userId)) throw forbidden("Board not owned by user");
        return board;
    }

    private void requireOwnedBoardByUserId(long boardId, long userId) {
        BoardEntity board = boardRepository.findById(boardId)
                .orElseThrow(() -> notFound("Board not found"));
        if (!board.getOwner().getId().equals(userId)) throw forbidden("Board not owned by user");
    }

    private static PlaybackState stoppedState(long boardId) {
        PlaybackState ps = new PlaybackState();
        ps.setBoardId(boardId);
        ps.setStatus(PlaybackStatus.STOPPED);
        return ps;
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

    private void removeSession(long boardId, BoardSession expected) {
        sessions.remove(boardId, expected);
    }

    private final class BoardSession {

        private final long boardId;

        private volatile AudioPlayer player;

        volatile PlaybackStatus status = PlaybackStatus.STOPPED;
        private volatile Long currentTrackId;
        private volatile Long windowStartS;
        private volatile Long windowEndS;

        private volatile long streamVersion;

        private volatile boolean trackFinished;

        private final BlockingQueue<byte[]> pcmFrames = new ArrayBlockingQueue<>(PCM_QUEUE_CAPACITY);
        private final AtomicReference<Runnable> activeCleanup = new AtomicReference<>();
        private final AtomicBoolean streaming = new AtomicBoolean(false);

        private volatile String cachedStreamToken;
        private volatile long cachedTokenUserId = -1;
        private volatile long cachedTokenVersion = -1;

        BoardSession(long boardId) {
            this.boardId = boardId;
        }

        PlaybackState snapshot() {
            PlaybackState ps = new PlaybackState();
            ps.setBoardId(boardId);
            ps.setStatus(status);
            ps.setTrackId(currentTrackId);
            ps.setWindowStartS(windowStartS);
            ps.setWindowEndS(windowEndS);

            AudioPlayer p = player;
            AudioTrack t = p != null ? p.getPlayingTrack() : null;
            ps.setPositionS(t != null ? Math.max(0L, t.getPosition() / 1000L) : null);

            if (status == PlaybackStatus.PLAYING) {
                Long userId = SecurityUtils.getCurrentUserId();
                long sv = streamVersion;
                if (cachedStreamToken == null || cachedTokenUserId != userId || cachedTokenVersion != sv) {
                    cachedStreamToken = jwtService.generateStreamToken(userId, boardId);
                    cachedTokenUserId = userId;
                    cachedTokenVersion = sv;
                }
                ps.setStreamUrl("/boards/" + boardId + "/stream?streamToken="
                        + cachedStreamToken + "&v=" + sv);
            }
            return ps;
        }

        void loadAndPlay(TrackEntity trackEntity) {
            stopInternal();

            AudioPlayer newPlayer = playerManager.createPlayer();
            this.player = newPlayer;

            status = PlaybackStatus.PLAYING;
            currentTrackId = trackEntity.getId();
            streamVersion++;
            trackFinished = false;
            cachedStreamToken = null;
            pcmFrames.clear();

            newPlayer.addListener(new AudioEventAdapter() {
                @Override
                public void onTrackStart(AudioPlayer p, AudioTrack track) {
                    status = PlaybackStatus.PLAYING;
                    log.debug("[board={}] track start: {} ({}ms)",
                            boardId, track.getInfo().title, track.getDuration());
                }

                @Override
                public void onTrackEnd(AudioPlayer p, AudioTrack track, AudioTrackEndReason reason) {
                    log.debug("[board={}] track end: {}", boardId, reason);
                    if (reason == AudioTrackEndReason.REPLACED) return;

                    trackFinished = true;
                }

                @Override
                public void onTrackStuck(AudioPlayer p, AudioTrack track, long thresholdMs) {
                    log.warn("[board={}] track stuck ({}ms)", boardId, thresholdMs);
                    status = PlaybackStatus.ERROR;
                    pcmFrames.clear();
                    removeSession(boardId, BoardSession.this);
                }

                @Override
                public void onTrackException(AudioPlayer p, AudioTrack track, FriendlyException ex) {
                    log.error("[board={}] track exception: {}", boardId, ex.getMessage(), ex);
                    status = PlaybackStatus.ERROR;
                    pcmFrames.clear();
                    removeSession(boardId, BoardSession.this);
                }
            });

            CountDownLatch latch = new CountDownLatch(1);

            playerManager.loadItem(trackEntity.getTrackLink(), new AudioLoadResultHandler() {
                @Override
                public void trackLoaded(AudioTrack track) {
                    applyWindowStart(track);
                    newPlayer.playTrack(track);
                    latch.countDown();
                }

                @Override
                public void playlistLoaded(AudioPlaylist playlist) {
                    AudioTrack first = playlist.getTracks().isEmpty() ? null : playlist.getTracks().getFirst();
                    if (first != null) {
                        applyWindowStart(first);
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
            removeSession(boardId, this);
        }

        private void stopInternal() {
            AudioPlayer p = player;
            if (p != null) {
                p.stopTrack();
                p.destroy();
                player = null;
            }
            status = PlaybackStatus.STOPPED;
            trackFinished = false;
            currentTrackId = null;
            pcmFrames.clear();
            closeStream();
            streaming.set(false);
            cachedStreamToken = null;
        }

        void setWindow(Long startS, Long endS) {
            this.windowStartS = startS;
            this.windowEndS = endS;
        }

        ResponseEntity<Resource> buildStreamResponse() throws IOException {
            Process ffmpeg = startFfmpeg();

            PipedOutputStream pos = new PipedOutputStream();
            PipedInputStream pis = new PipedInputStream(pos, PIPE_BUFFER_BYTES);

            AtomicBoolean cleaned = new AtomicBoolean(false);
            long myVersion = streamVersion;

            final Runnable[] holder = new Runnable[1];
            holder[0] = () -> {
                if (!cleaned.compareAndSet(false, true)) return;
                destroyQuietly(ffmpeg);
                closeQuietly(pos);
                activeCleanup.compareAndSet(holder[0], null);
                streaming.set(false);
            };
            Runnable cleanup = holder[0];

            closeStream();
            streaming.set(true);
            activeCleanup.set(cleanup);

            startDaemon("mp3-pump-" + boardId, () -> {
                try (InputStream ffOut = ffmpeg.getInputStream();
                     OutputStream out = new BufferedOutputStream(pos, 64 * 1024)) {
                    ffOut.transferTo(out);
                    out.flush();
                } catch (Exception ignored) {
                } finally {
                    cleanup.run();
                }
            });

            startDaemon("pcm-feeder-" + boardId, () -> {
                boolean naturalEnd = false;
                int written = 0;
                try (OutputStream in = new BufferedOutputStream(ffmpeg.getOutputStream(), 64 * 1024)) {
                    pcmFrames.clear();

                    while (!Thread.currentThread().isInterrupted()) {
                        if (status == PlaybackStatus.ERROR) break;
                        if (streamVersion != myVersion) break;
                        if (status == PlaybackStatus.STOPPED) break;

                        if (trackFinished) {
                            byte[] remaining;
                            while ((remaining = pcmFrames.poll(200, TimeUnit.MILLISECONDS)) != null) {
                                in.write(remaining);
                                written++;
                            }
                            in.flush();
                            naturalEnd = true;
                            break;
                        }

                        byte[] pcm = pcmFrames.poll(250, TimeUnit.MILLISECONDS);
                        if (pcm == null) continue;

                        in.write(pcm);
                        if (++written % 10 == 0) in.flush();
                    }
                    in.flush();
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                } catch (Exception ignored) {
                } finally {
                    status = PlaybackStatus.STOPPED;
                    currentTrackId = null;
                    trackFinished = false;
                    pcmFrames.clear();

                    if (naturalEnd) {
                        removeSession(boardId, BoardSession.this);
                    } else {
                        cleanup.run();
                    }
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

        private void closeStream() {
            Runnable c = activeCleanup.getAndSet(null);
            if (c != null) c.run();
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
                                pcmFrames.put(leftover.getData().clone());
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

                        pcmFrames.put(frame.getData().clone());
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
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