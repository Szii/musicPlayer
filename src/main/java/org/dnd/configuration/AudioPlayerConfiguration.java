package org.dnd.configuration;

import com.sedmelluq.discord.lavaplayer.format.StandardAudioDataFormats;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import dev.lavalink.youtube.YoutubeAudioSourceManager;
import dev.lavalink.youtube.clients.*;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.AuthSchemes;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.ProxyAuthenticationStrategy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;

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

  @Value("${youtube.proxy.host:}")
  private String youtubeProxyHost;

  @Value("${youtube.proxy.port:}")
  private String youtubeProxyPort;

  @Value("${youtube.proxy.username:}")
  private String youtubeProxyUsername;

  @Value("${youtube.proxy.password:}")
  private String youtubeProxyPassword;

  @Value("${youtube.proxy.allow:false}")
  private boolean youtubeProxyAllow;

  @Bean
  public AudioPlayerManager audioPlayerManager() {
    AudioPlayerManager mgr = new DefaultAudioPlayerManager();
    mgr.getConfiguration().setOutputFormat(StandardAudioDataFormats.COMMON_PCM_S16_LE);

    boolean hasPoToken = hasText(youtubePoToken) && hasText(youtubeVisitorData);

    if (hasPoToken) {
      Web.setPoTokenAndVisitorData(youtubePoToken, youtubeVisitorData);
    }

    YoutubeAudioSourceManager youtube = new YoutubeAudioSourceManager(
            true,
            new Web(),
            new Tv(),
            new MWeb(),
            new Music(),
            new AndroidVr(),
            new Ios()
    );

    configureYoutubeProxy(youtube);

    if (!hasPoToken) {
      if (hasText(youtubeOauthRefreshToken)) {
        youtube.useOauth2(youtubeOauthRefreshToken, true);
      } else if (youtubeOauthInteractive) {
        youtube.useOauth2(null, false);
      }
    }

    mgr.registerSourceManager(youtube);

    return mgr;
  }

  private void configureYoutubeProxy(YoutubeAudioSourceManager youtube) {
    if (!youtubeProxyAllow) {
      return;
    }

    if (!hasText(youtubeProxyHost)) {
      throw new IllegalStateException("youtube.proxy.allow=true but youtube.proxy.host is empty");
    }

    if (!hasText(youtubeProxyPort)) {
      throw new IllegalStateException("youtube.proxy.allow=true but youtube.proxy.port is empty");
    }

    if (!hasText(youtubeProxyUsername) || !hasText(youtubeProxyPassword)) {
      throw new IllegalStateException("youtube.proxy.allow=true but proxy username/password is empty");
    }

    int port;
    try {
      port = Integer.parseInt(youtubeProxyPort);
    } catch (NumberFormatException e) {
      throw new IllegalStateException("Invalid youtube.proxy.port: " + youtubeProxyPort, e);
    }

    if (port < 1 || port > 65535) {
      throw new IllegalStateException("youtube.proxy.port must be between 1 and 65535");
    }

    HttpHost proxy = new HttpHost(youtubeProxyHost, port, "http");

    youtube.getHttpInterfaceManager().configureRequests(config ->
            RequestConfig.copy(config)
                    .setProxy(proxy)
                    .setProxyPreferredAuthSchemes(Collections.singletonList(AuthSchemes.BASIC))
                    .build()
    );

    youtube.getHttpInterfaceManager().configureBuilder(builder -> {
      CredentialsProvider credentialsProvider = new BasicCredentialsProvider();

      credentialsProvider.setCredentials(
              new AuthScope(youtubeProxyHost, port),
              new UsernamePasswordCredentials(youtubeProxyUsername, youtubeProxyPassword)
      );

      builder.setDefaultCredentialsProvider(credentialsProvider)
              .setProxyAuthenticationStrategy(new ProxyAuthenticationStrategy());
    });
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}