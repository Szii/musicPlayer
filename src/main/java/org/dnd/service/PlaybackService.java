package org.dnd.service;

import com.sedmelluq.discord.lavaplayer.format.AudioDataFormat;
import com.sedmelluq.discord.lavaplayer.format.StandardAudioDataFormats;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import com.sedmelluq.discord.lavaplayer.track.playback.AudioFrame;
import dev.lavalink.youtube.YoutubeAudioSourceManager;
import lombok.extern.slf4j.Slf4j;
import org.dnd.api.model.PlayRequest;
import org.dnd.api.model.PlaybackState;
import org.dnd.api.model.PlaybackStatus;
import org.dnd.api.model.SeekRequest;
import org.dnd.model.BoardEntity;
import org.dnd.model.TrackEntity;
import org.dnd.repository.BoardRepository;
import org.dnd.repository.TrackRepository;
import org.dnd.utils.SecurityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.*;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.springframework.http.HttpStatus.*;

@Slf4j
@Service
public class PlaybackService {

    private final AudioPlayerManager playerManager;
    private final ExecutorService audioLoops;

    @Autowired
    private BoardRepository boardRepository;

    @Autowired
    private TrackRepository trackRepository;

    // One session per board (boardId -> session)
    private final ConcurrentMap<Long, BoardSession> sessions = new ConcurrentHashMap<>();

    public PlaybackService() {
        this.playerManager = new DefaultAudioPlayerManager();
        this.playerManager.getConfiguration().setOutputFormat(StandardAudioDataFormats.COMMON_PCM_S16_LE);
        this.playerManager.registerSourceManager(new YoutubeAudioSourceManager());

        this.audioLoops = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r);
            t.setName("board-audio-loop");
            t.setDaemon(true);
            return t;
        });
    }

    public PlaybackState getState(long boardId) {
        requireOwnedBoard(boardId);

        BoardSession s = sessions.get(boardId);
        if (s == null) {
            PlaybackState ps = new PlaybackState();
            ps.setBoardId(boardId);
            ps.setStatus(PlaybackStatus.STOPPED);
            return ps;
        }
        return s.toPlaybackState();
    }

    public PlaybackState play(long boardId, PlayRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        BoardEntity board = requireOwnedBoard(boardId);

        Long requestedTrackId = (request == null) ? null : request.getTrackId();

        TrackEntity trackToPlay;
        if (requestedTrackId != null) {
            trackToPlay = trackRepository.findById(requestedTrackId)
                    .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Track not found"));
        } else {
            trackToPlay = board.getSelectedTrack();
            if (trackToPlay == null) {
                throw new ResponseStatusException(CONFLICT, "No track selected on board (and no trackId provided)");
            }
        }

        if (!isTrackAccessible(userId, trackToPlay)) {
            throw new ResponseStatusException(FORBIDDEN, "Forbidden (track not accessible)");
        }

        BoardSession s = sessions.computeIfAbsent(boardId, id -> new BoardSession(id, playerManager));
        s.setWindowSeconds(null, null);
        s.loadAndPlay(trackToPlay);

        return s.toPlaybackState();
    }

    public PlaybackState stop(long boardId) {
        requireOwnedBoard(boardId);

        BoardSession s = requireSession(boardId);
        if (s.status == PlaybackStatus.STOPPED) {
            throw new ResponseStatusException(CONFLICT, "Action not valid in current playback state");
        }
        s.stop();
        return s.toPlaybackState();
    }

    public PlaybackState pause(long boardId) {
        requireOwnedBoard(boardId);

        BoardSession s = requireSession(boardId);
        if (s.status != PlaybackStatus.PLAYING) {
            throw new ResponseStatusException(CONFLICT, "Action not valid in current playback state");
        }
        s.pause();
        return s.toPlaybackState();
    }

    public PlaybackState resume(long boardId) {
        requireOwnedBoard(boardId);

        BoardSession s = requireSession(boardId);
        if (s.status != PlaybackStatus.PAUSED) {
            throw new ResponseStatusException(CONFLICT, "Action not valid in current playback state");
        }
        s.resume();
        return s.toPlaybackState();
    }

    public PlaybackState seek(long boardId, SeekRequest req) {
        Objects.requireNonNull(req, "seekRequest");
        requireOwnedBoard(boardId);

        BoardSession s = requireSession(boardId);

        if (s.player.getPlayingTrack() == null) {
            throw new ResponseStatusException(CONFLICT, "Action not valid in current playback state");
        }
        return s.toPlaybackState();
    }

    private BoardEntity requireOwnedBoard(long boardId) {
        Long userId = SecurityUtils.getCurrentUserId();
        BoardEntity board = boardRepository.findById(boardId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Board not found"));
        if (!board.getOwner().getId().equals(userId)) {
            throw new ResponseStatusException(FORBIDDEN, "Forbidden (board not owned by user)");
        }
        return board;
    }

    private BoardSession requireSession(long boardId) {
        BoardSession s = sessions.get(boardId);
        if (s == null) {
            throw new ResponseStatusException(CONFLICT, "Action not valid in current playback state");
        }
        return s;
    }

    private final class BoardSession {
        private final long boardId;
        private final AudioPlayer player;

        private final AtomicBoolean streaming = new AtomicBoolean(false);

        private volatile PlaybackStatus status = PlaybackStatus.STOPPED;
        private volatile Long currentTrackId = null;

        private volatile Long windowStartS = null;
        private volatile Long windowEndS = null;

        private final AtomicBoolean loopRunning = new AtomicBoolean(false);
        private final AtomicBoolean stopLoop = new AtomicBoolean(false);

        private final BlockingQueue<byte[]> pcmFrames = new LinkedBlockingQueue<>(200);

        private BoardSession(long boardId, AudioPlayerManager mgr) {
            this.boardId = boardId;
            this.player = mgr.createPlayer();

            this.player.addListener(new AudioEventAdapter() {
                @Override
                public void onTrackStart(AudioPlayer player, AudioTrack track) {
                    status = PlaybackStatus.PLAYING;
                    log.debug("durationMs={}, isStream={}, uri={}",
                            track.getDuration(), track.getInfo().isStream, track.getInfo().uri);
                    log.debug("[board={}] track start: {}", boardId, track.getInfo().title);
                }

                @Override
                public void onTrackStuck(AudioPlayer player, AudioTrack track, long thresholdMs) {
                    log.warn("[board={}] track stuck: thresholdMs={}", boardId, thresholdMs);
                    status = PlaybackStatus.ERROR;
                }

                @Override
                public void onTrackEnd(AudioPlayer player, AudioTrack track, AudioTrackEndReason endReason) {
                    log.debug("[board={}] track end: reason={}", boardId, endReason);
                    if (endReason.mayStartNext) {
                        // TODO repeat track based on board state
                    }
                    status = PlaybackStatus.STOPPED;
                    currentTrackId = null;
                    stopInternalLoop();
                    pcmFrames.clear();
                    streaming.set(false);
                }

                @Override
                public void onPlayerPause(AudioPlayer player) {
                    status = PlaybackStatus.PAUSED;
                }

                @Override
                public void onPlayerResume(AudioPlayer player) {
                    status = PlaybackStatus.PLAYING;
                }

                @Override
                public void onTrackException(AudioPlayer player, AudioTrack track, FriendlyException exception) {
                    log.error("[board={}] track exception: {}", boardId, exception.getMessage(), exception);
                    status = PlaybackStatus.ERROR;
                }
            });
        }

        private PlaybackState toPlaybackState() {
            PlaybackState ps = new PlaybackState();
            ps.setBoardId(boardId);
            ps.setStatus(status);
            ps.setTrackId(currentTrackId);
            ps.setWindowStartS(windowStartS);
            ps.setWindowEndS(windowEndS);


            AudioTrack t = player.getPlayingTrack();
            if (t != null) {
                ps.setPositionS(Math.max(0L, t.getPosition() / 1000L));
            } else {
                ps.setPositionS(null);
            }
            return ps;
        }

        private void loadAndPlay(TrackEntity trackResolved) {
            status = PlaybackStatus.BUFFERING;
            currentTrackId = trackResolved.getId();
            pcmFrames.clear();

            CountDownLatch latch = new CountDownLatch(1);

            playerManager.loadItem(trackResolved.getTrackLink(), new AudioLoadResultHandler() {
                @Override
                public void trackLoaded(AudioTrack track) {
                    //TODO make it seekable / clone-proof
                    player.playTrack(track);
                    latch.countDown();
                }

                @Override
                public void playlistLoaded(AudioPlaylist playlist) {
                    AudioTrack first = playlist.getTracks().isEmpty() ? null : playlist.getTracks().get(0);
                    if (first != null) player.playTrack(first);
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
                if (!latch.await(10, TimeUnit.SECONDS)) {
                    status = PlaybackStatus.ERROR;
                    throw new ResponseStatusException(GATEWAY_TIMEOUT, "Timeout while loading track");
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                status = PlaybackStatus.ERROR;
                throw new ResponseStatusException(INTERNAL_SERVER_ERROR, "Interrupted while loading track");
            }

            if (player.getPlayingTrack() == null) {
                status = PlaybackStatus.ERROR;
                throw new ResponseStatusException(NOT_FOUND, "Board or track not found");
            }

            startInternalLoopIfNeeded();
        }

        private void pause() {
            player.setPaused(true);
        }

        private void resume() {
            player.setPaused(false);
        }

        private void stop() {
            player.stopTrack();
            status = PlaybackStatus.STOPPED;
            currentTrackId = null;
            stopInternalLoop();
            pcmFrames.clear();
            streaming.set(false);
        }

        private void setWindowSeconds(Long startS, Long endS) {
            this.windowStartS = startS;
            this.windowEndS = endS;
        }

        //TODO implement cutting track according to track points
        private void startInternalLoopIfNeeded() {
            if (!loopRunning.compareAndSet(false, true)) {
                return;
            }
            stopLoop.set(false);

            audioLoops.submit(() -> {
                try {
                    while (!stopLoop.get()) {
                        AudioTrack t = player.getPlayingTrack();
                        if (t == null) {
                            sleepQuiet(20);
                            continue;
                        }

                        // stop at window end
                        if (windowEndS != null) {
                            long endMs = windowEndS * 1000L;
                            if (t.getPosition() >= endMs) {
                                player.stopTrack();
                                continue;
                            }
                        }

                        AudioFrame frame = player.provide();
                        if (frame == null) {
                            sleepQuiet(10);
                            continue;
                        }

                        //TODO remove this behaviour: If queue is full, drop oldest
                        byte[] data = frame.getData();
                        if (!pcmFrames.offer(data)) {
                            pcmFrames.poll();
                            pcmFrames.offer(data);
                        }
                        sleepQuiet(20);
                    }
                } finally {
                    loopRunning.set(false);
                }
            });
        }

        private void stopInternalLoop() {
            stopLoop.set(true);
        }

        private static void sleepQuiet(long ms) {
            try {
                Thread.sleep(ms);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private boolean isTrackAccessible(Long userId, TrackEntity track) {
        if (track.getOwner().getId().equals(userId)) {
            return true;
        }

        return track.getShares().stream()
                .anyMatch(share -> share.getUser().getId().equals(userId));
    }


    public ResponseEntity<Resource> streamMp3(long boardId) {
        requireOwnedBoard(boardId);

        BoardSession s = sessions.get(boardId);
        if (s == null || (s.status != PlaybackStatus.PLAYING && s.status != PlaybackStatus.PAUSED)) {
            throw new ResponseStatusException(CONFLICT, "Board is not playing");
        }

        if (!s.streaming.compareAndSet(false, true)) {
            throw new ResponseStatusException(CONFLICT, "Stream already active for this board");
        }

        Process ffmpeg;
        Thread feeder;
        Thread mp3Pump;

        try {
            ffmpeg = startFfmpegMp3Process();

            PipedOutputStream pos = new PipedOutputStream();
            PipedInputStream pis = new PipedInputStream(pos, 64 * 1024);

            AtomicBoolean cleaned = new AtomicBoolean(false);

            Runnable cleanup = () -> {
                if (!cleaned.compareAndSet(false, true)) return;

                try {
                    pis.close();
                } catch (Exception ignored) {
                }
                try {
                    pos.close();
                } catch (Exception ignored) {
                }

                try {
                    ffmpeg.destroy();
                } catch (Exception ignored) {
                }

                try {
                    s.streaming.set(false);
                } catch (Exception ignored) {
                }
            };

            // ffmpeg output piped to http response via resource stream
            mp3Pump = new Thread(() -> {
                try (var ffOut = ffmpeg.getInputStream(); var out = pos) {
                    ffOut.transferTo(out);
                } catch (Exception ignored) {
                } finally {
                    cleanup.run();
                }
            }, "mp3-pump-" + boardId);
            mp3Pump.setDaemon(true);
            mp3Pump.start();

            // PCM frames conversion to ffmpeg output
            feeder = new Thread(() -> {
                try (var in = ffmpeg.getOutputStream()) {
                    while (!Thread.currentThread().isInterrupted()) {
                        if (s.status == PlaybackStatus.STOPPED || s.status == PlaybackStatus.ERROR) break;

                        byte[] pcm = s.pcmFrames.poll(250, TimeUnit.MILLISECONDS);
                        if (pcm == null) continue;

                        in.write(pcm);
                    }
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                } catch (Exception ignored) {
                } finally {
                    cleanup.run();
                }
            }, "pcm-feeder-" + boardId);
            feeder.setDaemon(true);
            feeder.start();

            InputStream closableStream = new FilterInputStream(pis) {
                @Override
                public void close() throws IOException {
                    try {
                        super.close();
                    } finally {
                        cleanup.run();
                    }
                }
            };

            Resource body = new InputStreamResource(closableStream);

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("audio/mpeg"))
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .header("X-Content-Type-Options", "nosniff")
                    .body(body);

        } catch (Exception e) {
            s.streaming.set(false);
            throw new ResponseStatusException(INTERNAL_SERVER_ERROR, "Streaming failed");
        }
    }

    private Process startFfmpegMp3Process() throws IOException {
        AudioDataFormat fmt = playerManager.getConfiguration().getOutputFormat();

        int sampleRate = fmt.sampleRate;
        int channels = fmt.channelCount;

        log.debug("Starting ffmpeg with PCM format: {} Hz, {} ch", sampleRate, channels);

        ProcessBuilder pb = new ProcessBuilder(
                "ffmpeg",
                "-hide_banner",
                "-loglevel", "error",
                "-f", "s16le",
                "-ar", String.valueOf(sampleRate),
                "-ac", String.valueOf(channels),
                "-i", "pipe:0",
                "-f", "mp3",
                "-codec:a", "libmp3lame",
                "-b:a", "192k",
                "pipe:1"
        );

        pb.redirectError(ProcessBuilder.Redirect.INHERIT);
        return pb.start();
    }
}
