package org.dnd.configuration;

import com.sedmelluq.discord.lavaplayer.format.StandardAudioDataFormats;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import dev.lavalink.youtube.YoutubeAudioSourceManager;
import dev.lavalink.youtube.clients.AndroidVr;
import dev.lavalink.youtube.clients.Ios;
import dev.lavalink.youtube.clients.MWeb;
import dev.lavalink.youtube.clients.Music;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AudioPlayerConfiguration {

  @Bean
  public AudioPlayerManager audioPlayerManager() {
    AudioPlayerManager mgr = new DefaultAudioPlayerManager();
    mgr.getConfiguration().setOutputFormat(StandardAudioDataFormats.COMMON_PCM_S16_LE);

    YoutubeAudioSourceManager youtube = new YoutubeAudioSourceManager(
            true,
            new Music(),
            new AndroidVr(),
            new Ios(),
            new MWeb());

    mgr.registerSourceManager(youtube);

    return mgr;
  }
}