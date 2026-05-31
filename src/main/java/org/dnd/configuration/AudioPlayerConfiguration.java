package org.dnd.configuration;

import com.sedmelluq.discord.lavaplayer.format.StandardAudioDataFormats;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import dev.lavalink.youtube.YoutubeAudioSourceManager;
import dev.lavalink.youtube.clients.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AudioPlayerConfiguration {

  @Value("${youtube.pot.token:}")
  private String youtubePoToken;

  @Value("${youtube.pot.visitor-data:}")
  private String youtubeVisitorData;

  @Value("${youtube.oauth.refresh-token:}")
  private String youtubeOauthRefreshToken;

  @Value("${youtube.oauth.interactive:false}")
  private boolean youtubeOauthInteractive;

  @Bean
  public AudioPlayerManager audioPlayerManager() {
    AudioPlayerManager mgr = new DefaultAudioPlayerManager();
    mgr.getConfiguration().setOutputFormat(StandardAudioDataFormats.COMMON_PCM_S16_LE);

    boolean hasPoToken = youtubePoToken != null && !youtubePoToken.isBlank()
            && youtubeVisitorData != null && !youtubeVisitorData.isBlank();

    if (hasPoToken) {
      Web.setPoTokenAndVisitorData(youtubePoToken, youtubeVisitorData);
    }

    YoutubeAudioSourceManager youtube = new YoutubeAudioSourceManager(
            true,
            new Tv(),
            new MWeb(),
            new Music(),
            new AndroidVr(),
            new Ios()
    );

    if (!hasPoToken) {
      if (youtubeOauthRefreshToken != null && !youtubeOauthRefreshToken.isBlank()) {
        youtube.useOauth2(youtubeOauthRefreshToken, true);
      } else if (youtubeOauthInteractive) {
        youtube.useOauth2(null, false);
      }
    }

    mgr.registerSourceManager(youtube);
    ;

    if (youtubeOauthRefreshToken != null && !youtubeOauthRefreshToken.isBlank()) {
      youtube.useOauth2(youtubeOauthRefreshToken, true);
    } else if (youtubeOauthInteractive) {
      youtube.useOauth2(null, false);
    }

    mgr.registerSourceManager(youtube);

    return mgr;
  }
}