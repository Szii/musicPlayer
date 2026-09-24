package org.dnd.session.share;

import org.dnd.board.LinkedBoardMode;

import java.util.List;
import java.util.UUID;

public record SessionSnapshot(
        String name,
        String description,
        List<TrackSnapshot> tracks,
        List<GroupSnapshot> groups,
        List<UUID> sessionTrackIds,
        List<BoardSnapshot> boards
) {

  public record TrackSnapshot(
          UUID id,
          String trackName,
          String trackOriginalName,
          String trackLink,
          int duration,
          int fadeInDurationMs,
          int fadeOutDurationMs,
          List<WindowSnapshot> windows
  ) {
  }

  public record WindowSnapshot(
          UUID id,
          String name,
          Long positionFrom,
          Long positionTo,
          int fadeInDurationMs,
          int fadeOutDurationMs,
          int positionWithinTrack
  ) {
  }

  public record GroupSnapshot(
          UUID id,
          String listName,
          List<GroupTrackSnapshot> tracks
  ) {
  }

  public record GroupTrackSnapshot(
          UUID trackId,
          UUID windowId,
          String customName,
          int positionWithinGroup
  ) {
  }

  public record BoardSnapshot(
          UUID id,
          String name,
          UUID selectedTrackId,
          UUID selectedWindowId,
          UUID selectedGroupId,
          int volume,
          boolean repeat,
          boolean overplay,
          boolean shuffle,
          boolean playlistMode,
          boolean sequenceMode,
          UUID linkedBoardId,
          LinkedBoardMode linkedBoardMode
  ) {
  }
}
