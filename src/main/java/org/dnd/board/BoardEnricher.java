package org.dnd.board;

import lombok.RequiredArgsConstructor;
import org.dnd.api.model.Board;
import org.dnd.api.model.Track;
import org.dnd.session.SessionEntity;
import org.dnd.track.TrackEntity;
import org.dnd.track.TrackMapper;
import org.dnd.track.TrackRepository;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BoardEnricher {

  private final TrackRepository trackRepository;
  private final TrackMapper trackMapper;

  public Board enrich(Board boardDto, BoardEntity boardEntity, UUID userId) {
    boardDto.setAvailableTracks(getAvailableTracks(boardEntity, userId));
    return boardDto;
  }

  public List<Track> getAvailableTracks(BoardEntity boardEntity, UUID userId) {
    List<TrackEntity> tracks;

    if (boardEntity.getSelectedGroup() != null) {
      tracks = trackRepository.findByGroupTracks_Group_Id(boardEntity.getSelectedGroup().getId());
    } else {
      tracks = getSessionTracks(boardEntity);
    }

    return tracks.stream()
            .map(trackEntity -> trackMapper.toDto(trackEntity, userId))
            .toList();
  }

  private List<TrackEntity> getSessionTracks(BoardEntity boardEntity) {
    SessionEntity session = boardEntity.getSession();
    Set<TrackEntity> tracks = new LinkedHashSet<>(session.getTracks());
    session.getGroups().forEach(group ->
            group.getGroupTracks().forEach(groupTrack -> tracks.add(groupTrack.getTrack())));
    if (boardEntity.getSelectedTrack() != null) {
      tracks.add(boardEntity.getSelectedTrack());
    }
    return List.copyOf(tracks);
  }
}
