package org.dnd.session.share;

import org.dnd.board.BoardEntity;
import org.dnd.board.LinkedBoard;
import org.dnd.group.GroupEntity;
import org.dnd.group.GroupTrackEntity;
import org.dnd.session.SessionEntity;
import org.dnd.session.share.SessionSnapshot.BoardSnapshot;
import org.dnd.session.share.SessionSnapshot.GroupSnapshot;
import org.dnd.session.share.SessionSnapshot.GroupTrackSnapshot;
import org.dnd.session.share.SessionSnapshot.TrackSnapshot;
import org.dnd.session.share.SessionSnapshot.WindowSnapshot;
import org.dnd.track.TrackEntity;
import org.dnd.track.TrackWindowEntity;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class SessionSnapshotFactory {

  public SessionSnapshot create(SessionEntity session) {
    Map<UUID, TrackEntity> tracks = new LinkedHashMap<>();
    session.getTracks().forEach(track -> tracks.put(track.getId(), track));
    session.getGroups().forEach(group ->
            group.getGroupTracks().forEach(groupTrack -> tracks.put(groupTrack.getTrack().getId(), groupTrack.getTrack())));
    boards(session).forEach(board -> {
      if (board.getSelectedTrack() != null) {
        tracks.put(board.getSelectedTrack().getId(), board.getSelectedTrack());
      }
    });

    Set<UUID> boardIds = boards(session).stream().map(BoardEntity::getId).collect(Collectors.toSet());

    Set<UUID> sessionTrackIds = new LinkedHashSet<>();
    session.getTracks().forEach(track -> sessionTrackIds.add(track.getId()));
    boards(session).forEach(board -> {
      if (board.getSelectedGroup() == null && board.getSelectedTrack() != null) {
        sessionTrackIds.add(board.getSelectedTrack().getId());
      }
    });

    return new SessionSnapshot(
            session.getName(),
            session.getDescription(),
            tracks.values().stream().map(this::toTrack).toList(),
            session.getGroups().stream().map(this::toGroup).toList(),
            List.copyOf(sessionTrackIds),
            boards(session).stream().map(board -> toBoard(board, boardIds)).toList()
    );
  }

  private List<BoardEntity> boards(SessionEntity session) {
    return session.getBoards() == null ? List.of() : List.copyOf(session.getBoards());
  }

  private TrackSnapshot toTrack(TrackEntity track) {
    return new TrackSnapshot(
            track.getId(),
            track.getTrackName(),
            track.getTrackOriginalName(),
            track.getTrackLink(),
            track.getDuration(),
            track.getFadeInDurationMs(),
            track.getFadeOutDurationMs(),
            track.getTrackWindows().stream().map(this::toWindow).toList()
    );
  }

  private WindowSnapshot toWindow(TrackWindowEntity window) {
    return new WindowSnapshot(
            window.getId(),
            window.getName(),
            window.getPositionFrom(),
            window.getPositionTo(),
            window.getFadeInDurationMs(),
            window.getFadeOutDurationMs(),
            window.getPositionWithinTrack()
    );
  }

  private GroupSnapshot toGroup(GroupEntity group) {
    return new GroupSnapshot(
            group.getId(),
            group.getListName(),
            group.getGroupTracks().stream()
                    .sorted(Comparator.comparingInt(GroupTrackEntity::getPositionWithinGroup))
                    .map(groupTrack -> new GroupTrackSnapshot(
                            groupTrack.getTrack().getId(),
                            groupTrack.getTrackWindow() == null ? null : groupTrack.getTrackWindow().getId(),
                            groupTrack.getCustomName(),
                            groupTrack.getPositionWithinGroup()))
                    .toList()
    );
  }

  private BoardSnapshot toBoard(BoardEntity board, Set<UUID> boardIds) {
    LinkedBoard linkedBoard = board.getLinkedBoard();
    boolean linkedInSession = linkedBoard != null
            && linkedBoard.getBoardId() != null
            && boardIds.contains(linkedBoard.getBoardId());

    return new BoardSnapshot(
            board.getId(),
            board.getName(),
            board.getSelectedTrack() == null ? null : board.getSelectedTrack().getId(),
            board.getSelectedWindow() == null ? null : board.getSelectedWindow().getId(),
            board.getSelectedGroup() == null ? null : board.getSelectedGroup().getId(),
            board.getVolume(),
            board.isRepeat(),
            board.isOverplay(),
            board.isShuffle(),
            board.isPlaylistMode(),
            board.isSequenceMode(),
            linkedInSession ? linkedBoard.getBoardId() : null,
            linkedInSession ? linkedBoard.getMode() : null
    );
  }
}
