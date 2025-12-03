package org.dnd;

import com.sedmelluq.discord.lavaplayer.format.StandardAudioDataFormats;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.AudioFrame;
import dev.lavalink.youtube.YoutubeAudioSourceManager;
import lombok.extern.slf4j.Slf4j;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;


@Slf4j
public class AudioPlayerCore {
    private AudioPlayerManager playerManager;
    private AudioPlayer player;
    private SourceDataLine speakerLine;

    public AudioPlayerCore() {
        try {
            setupAudioPlayer();
        } catch (LineUnavailableException e) {
            log.error(e.getMessage());
        }
    }

    private void setupAudioPlayer() throws LineUnavailableException {
        playerManager = new DefaultAudioPlayerManager();
        playerManager.getConfiguration().setOutputFormat(StandardAudioDataFormats.COMMON_PCM_S16_LE);
        playerManager.registerSourceManager(new YoutubeAudioSourceManager());
        player = playerManager.createPlayer();
        AudioFormat format = new AudioFormat(48000f, 16, 2, true, false);
        speakerLine = AudioSystem.getSourceDataLine(format);
        speakerLine.open(format);
        speakerLine.start();
    }

    public void loadTrack(String ytLink) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);

        playerManager.loadItem(ytLink, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                log.debug("Loading track with original title: {}", track.getInfo().title);
                player.playTrack(track);
                latch.countDown();
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                log.debug("Loading playlist: {}", playlist.getName());
                AudioTrack first = playlist.getTracks().getFirst();
                log.debug("Playing only first entry");
                player.playTrack(first);
                latch.countDown();
            }

            @Override
            public void noMatches() {
                log.warn("No track found for {}", ytLink);
                latch.countDown();
            }

            @Override
            public void loadFailed(FriendlyException e) {
                log.debug("Loading failed for link {}", ytLink);
                log.error(e.getMessage(), e);
                latch.countDown();
            }
        });

        if (!latch.await(10, TimeUnit.SECONDS)) {
            log.warn("Timeout while loading track with link {}", ytLink);
            return;
        }

        if (player.getPlayingTrack() == null) {
            log.warn("Cannot get playing track, omitting playing");
            return;
        }

        play();
    }

    private void play() throws InterruptedException {
        while (player.getPlayingTrack() != null) {
            AudioFrame frame = player.provide();
            if (frame != null) {
                byte[] data = frame.getData();
                speakerLine.write(data, 0, data.length);
            } else {
                TimeUnit.MILLISECONDS.sleep(10);
            }
        }

        log.info("Playback finished");

        speakerLine.drain();
        speakerLine.close();
    }
}
