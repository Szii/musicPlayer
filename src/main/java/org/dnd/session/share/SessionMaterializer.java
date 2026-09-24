package org.dnd.session.share;

import lombok.RequiredArgsConstructor;
import org.dnd.board.BoardEntity;
import org.dnd.board.BoardRepository;
import org.dnd.board.LinkedBoard;
import org.dnd.group.GroupEntity;
import org.dnd.group.GroupRepository;
import org.dnd.group.GroupTrackEntity;
import org.dnd.session.SessionEntity;
import org.dnd.session.share.SessionSnapshot.BoardSnapshot;
import org.dnd.session.share.SessionSnapshot.GroupSnapshot;
import org.dnd.session.share.SessionSnapshot.GroupTrackSnapshot;
import org.dnd.session.share.SessionSnapshot.TrackSnapshot;
import org.dnd.session.share.SessionSnapshot.WindowSnapshot;
import org.dnd.track.TrackEntity;
import org.dnd.track.TrackRepository;
import org.dnd.track.TrackWindowEntity;
import org.dnd.user.UserEntity;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
public class SessionMaterializer {

  private final TrackRepository trackRepository;
  private final GroupRepository groupRepository;
  private final BoardRepository boardRepository;

  public void clear(SessionEntity session) {
    session.getBoards().clear();
    session.getTracks().clear();
    session.getGroups().clear();
    boardRepository.flush();

    groupRepository.deleteAll(groupRepository.findByManagedSession_Id(session.getId()));
    groupRepository.flush();

    trackRepository.deleteAll(trackRepository.findByManagedSession_Id(session.getId()));
    trackRepository.flush();
  }

  public void materialize(SessionSnapshot snapshot, SessionEntity session) {
    UserEntity owner = session.getOwner();
    session.setName(snapshot.name());
    session.setDescription(snapshot.description());

    Map<UUID, TrackEntity> tracks = new HashMap<>();
    Map<UUID, TrackWindowEntity> windows = new HashMap<>();
    for (TrackSnapshot trackSnapshot : snapshot.tracks()) {
      TrackEntity track = new TrackEntity();
      track.setTrackName(trackSnapshot.trackName());
      track.setTrackOriginalName(trackSnapshot.trackOriginalName());
      track.setTrackLink(trackSnapshot.trackLink());
      track.setDuration(trackSnapshot.duration());
      track.setFadeInDurationMs(trackSnapshot.fadeInDurationMs());
      track.setFadeOutDurationMs(trackSnapshot.fadeOutDurationMs());
      track.setOwner(owner);
      track.setManagedSession(session);

      for (WindowSnapshot windowSnapshot : trackSnapshot.windows()) {
        TrackWindowEntity window = TrackWindowEntity.builder()
                .name(windowSnapshot.name())
                .positionFrom(windowSnapshot.positionFrom())
                .positionTo(windowSnapshot.positionTo())
                .fadeInDurationMs(windowSnapshot.fadeInDurationMs())
                .fadeOutDurationMs(windowSnapshot.fadeOutDurationMs())
                .positionWithinTrack(windowSnapshot.positionWithinTrack())
                .build();
        track.addTrackWindow(window);
        windows.put(windowSnapshot.id(), window);
      }

      tracks.put(trackSnapshot.id(), trackRepository.save(track));
    }

    Map<UUID, GroupEntity> groups = new HashMap<>();
    for (GroupSnapshot groupSnapshot : snapshot.groups()) {
      GroupEntity group = new GroupEntity();
      group.setListName(groupSnapshot.listName());
      group.setOwner(owner);
      group.setManagedSession(session);

      for (GroupTrackSnapshot item : groupSnapshot.tracks()) {
        TrackEntity track = tracks.get(item.trackId());
        TrackWindowEntity window = item.windowId() == null ? null : windows.get(item.windowId());
        if (track == null || (item.windowId() != null && window == null)) {
          continue;
        }
        group.addTrack(track, window, item.customName()).setPositionWithinGroup(item.positionWithinGroup());
      }

      groups.put(groupSnapshot.id(), groupRepository.save(group));
    }

    snapshot.sessionTrackIds().stream()
            .map(tracks::get)
            .filter(Objects::nonNull)
            .forEach(session.getTracks()::add);
    session.getGroups().addAll(groups.values());

    Map<UUID, BoardEntity> boards = new HashMap<>();
    for (BoardSnapshot boardSnapshot : snapshot.boards()) {
      BoardEntity board = new BoardEntity();
      board.setName(boardSnapshot.name());
      board.setOwner(owner);
      board.setSession(session);
      board.setSelectedGroup(boardSnapshot.selectedGroupId() == null ? null : groups.get(boardSnapshot.selectedGroupId()));
      if (boardSnapshot.selectedTrackId() == null) {
        selectFirstTrack(board, snapshot, tracks);
      } else {
        board.setSelectedTrack(tracks.get(boardSnapshot.selectedTrackId()));
        board.setSelectedWindow(boardSnapshot.selectedWindowId() == null ? null : windows.get(boardSnapshot.selectedWindowId()));
      }
      board.setVolume(boardSnapshot.volume());
      board.setRepeat(boardSnapshot.repeat());
      board.setOverplay(boardSnapshot.overplay());
      board.setShuffle(boardSnapshot.shuffle());
      board.setPlaylistMode(boardSnapshot.playlistMode());
      board.setSequenceMode(boardSnapshot.sequenceMode());

      session.getBoards().add(board);
      boards.put(boardSnapshot.id(), boardRepository.save(board));
    }

    for (BoardSnapshot boardSnapshot : snapshot.boards()) {
      BoardEntity linked = boardSnapshot.linkedBoardId() == null ? null : boards.get(boardSnapshot.linkedBoardId());
      if (linked != null) {
        boards.get(boardSnapshot.id()).setLinkedBoard(new LinkedBoard(linked.getId(), boardSnapshot.linkedBoardMode()));
      }
    }
  }

  private void selectFirstTrack(BoardEntity board,
                                SessionSnapshot snapshot,
                                Map<UUID, TrackEntity> tracks) {
    if (board.getSelectedGroup() != null) {
      board.getSelectedGroup().getGroupTracks().stream()
              .min(Comparator.comparingInt(GroupTrackEntity::getPositionWithinGroup))
              .ifPresent(first -> {
                board.setSelectedTrack(first.getTrack());
                board.setSelectedWindow(first.getTrackWindow());
              });
      return;
    }

    Stream.concat(
                    snapshot.sessionTrackIds().stream(),
                    snapshot.groups().stream().flatMap(group -> group.tracks().stream().map(GroupTrackSnapshot::trackId)))
            .map(tracks::get)
            .filter(Objects::nonNull)
            .findFirst()
            .ifPresent(board::setSelectedTrack);
  }
}
