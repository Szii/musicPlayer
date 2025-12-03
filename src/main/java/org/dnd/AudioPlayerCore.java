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
import java.util.concurrent.TimeUnit;


@Slf4j
public class AudioPlayerCore {
    private AudioPlayerManager playerManager;
    private AudioPlayer player;
    private AudioFormat format;
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
        format = new AudioFormat(48000f, 16, 2, true, false);
        speakerLine = AudioSystem.getSourceDataLine(format);
        speakerLine.open(format);
        speakerLine.start();
    }

    private void loadTrack(String ytLink) {
        playerManager.loadItem(ytLink, new AudioLoadResultHandler() {
            final AudioTrack[] loadedTrack = new AudioTrack[1];

            @Override
            public void trackLoaded(AudioTrack track) {
                loadedTrack[0] = track;
                player.playTrack(track);
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                loadedTrack[0] = playlist.getTracks().getFirst();
                player.playTrack(loadedTrack[0]);
            }

            @Override
            public void noMatches() {
                log.warn("No track found for {}", ytLink);
            }

            @Override
            public void loadFailed(FriendlyException e) {
                log.error(e.getMessage());
            }
        });
    }

    public void playTrack(String ytLink) throws InterruptedException {
        loadTrack(ytLink);

        while (true) {
            AudioFrame frame = player.provide();
            if (frame != null) {
                byte[] data = frame.getData();
                speakerLine.write(data, 0, data.length);
            } else {
                TimeUnit.MILLISECONDS.sleep(10);
                if (player.getPlayingTrack() == null) break;
            }
        }
        clearMemory();
    }

    private void clearMemory() {
        speakerLine.drain();
        speakerLine.close();
        playerManager.shutdown();
    }
}
