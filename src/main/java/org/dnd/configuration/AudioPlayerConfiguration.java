package org.dnd.configuration;

import com.sedmelluq.discord.lavaplayer.format.StandardAudioDataFormats;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import dev.lavalink.youtube.YoutubeAudioSourceManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AudioPlayerConfiguration {
    @Bean
    public AudioPlayerManager audioPlayerManager() {
        AudioPlayerManager mgr = new DefaultAudioPlayerManager();
        mgr.getConfiguration().setOutputFormat(StandardAudioDataFormats.COMMON_PCM_S16_LE);

        mgr.registerSourceManager(new YoutubeAudioSourceManager());

        return mgr;
    }
}
