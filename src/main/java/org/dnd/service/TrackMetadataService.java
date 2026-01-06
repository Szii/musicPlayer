package org.dnd.service;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import org.dnd.model.TrackMetadata;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
public class TrackMetadataService {

    private final AudioPlayerManager playerManager;

    public TrackMetadataService(AudioPlayerManager playerManager) {
        this.playerManager = playerManager;
    }

    public TrackMetadata resolveMetadata(String trackLink) {
        if (trackLink == null || trackLink.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "trackLink is empty");
        }

        CompletableFuture<TrackMetadata> future = new CompletableFuture<>();

        playerManager.loadItem(trackLink, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                future.complete(toMetadata(track));
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                AudioTrack first = playlist.getTracks().isEmpty() ? null : playlist.getTracks().get(0);
                if (first == null) {
                    future.completeExceptionally(new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "Playlist loaded but has no tracks"));
                    return;
                }
                future.complete(toMetadata(first));
            }

            @Override
            public void noMatches() {
                future.completeExceptionally(new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "No matches for: " + trackLink));
            }

            @Override
            public void loadFailed(FriendlyException exception) {
                future.completeExceptionally(new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY, "Failed to load track: " + exception.getMessage(), exception));
            }
        });

        try {
            // tune timeout as you like
            return future.get(10, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "Timeout while resolving metadata");
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException re) throw re;
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Metadata resolve failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Interrupted while resolving metadata");
        }
    }

    private static TrackMetadata toMetadata(AudioTrack track) {
        AudioTrackInfo info = track.getInfo();
        return new TrackMetadata(
                info.title,
                info.author,
                info.uri,
                info.identifier,
                info.isStream,
                track.getDuration()
        );
    }
}
