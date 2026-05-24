package org.dnd.board;

import lombok.RequiredArgsConstructor;
import org.dnd.api.model.Board;
import org.dnd.api.model.Track;
import org.dnd.track.TrackEntity;
import org.dnd.track.TrackMapper;
import org.dnd.track.TrackRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BoardEnricher {

  private final TrackRepository trackRepository;
  private final TrackMapper trackMapper;

  public Board enrich(Board boardDto, BoardEntity boardEntity, Long userId) {
    boardDto.setAvailableTracks(getAvailableTracks(boardEntity, userId));
    return boardDto;
  }

  public List<Track> getAvailableTracks(BoardEntity boardEntity, Long userId) {
    List<TrackEntity> tracks;

    if (boardEntity.getSelectedGroup() != null) {
      tracks = trackRepository.findByGroups_Id(boardEntity.getSelectedGroup().getId());
    } else {
      tracks = trackRepository.findAllAccessibleByUserId(userId);
    }

    return tracks.stream()
            .map(trackEntity -> trackMapper.toDto(trackEntity, userId))
            .toList();
  }
}
