package org.dnd.rank;

import org.dnd.api.model.UserLimits;
import org.dnd.board.BoardEntity;
import org.dnd.session.SessionEntity;
import org.dnd.session.SessionRepository;
import org.dnd.track.TrackEntity;
import org.dnd.user.UserEntity;
import org.dnd.user.UserRank;
import org.dnd.user.rank.UserRankEvaluatorService;
import org.dnd.user.rank.UserRankLimitProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserRankEvaluatorServiceTest {

  @Mock
  private SessionRepository sessionRepository;

  private UserRankEvaluatorService service;

  @BeforeEach
  void setUp() {
    service = new UserRankEvaluatorService(
            sessionRepository,
            new UserRankLimitProvider()
    );
  }

  @Test
  void normalUserCanCreateTrackWhenBelowLimit() {
    UserEntity user = mock(UserEntity.class);

    when(user.getRank()).thenReturn(UserRank.NORMAL);
    when(user.getOwnedTracks()).thenReturn(tracks(9));

    assertThat(service.canCreateTrack(user)).isTrue();
  }

  @Test
  void normalUserCannotCreateTrackWhenLimitReached() {
    UserEntity user = mock(UserEntity.class);

    when(user.getRank()).thenReturn(UserRank.NORMAL);
    when(user.getOwnedTracks()).thenReturn(tracks(10));

    assertThat(service.canCreateTrack(user)).isFalse();
  }

  @Test
  void unrestrictedUserCanCreateTrackEvenOverLimit() {
    UserEntity user = mock(UserEntity.class);

    when(user.getRank()).thenReturn(UserRank.UNRESTRICTED);
    when(user.getOwnedTracks()).thenReturn(tracks(100));

    assertThat(service.canCreateTrack(user)).isTrue();
  }

  @Test
  void normalUserCannotCreateSessionWhenLimitReached() {
    UserEntity user = mock(UserEntity.class);

    when(user.getId()).thenReturn(1L);
    when(user.getRank()).thenReturn(UserRank.NORMAL);
    when(sessionRepository.findByOwner_Id(1L)).thenReturn(rawList(5));

    assertThat(service.canCreateSession(user)).isFalse();
  }

  @Test
  void normalUserCannotCreateTrackWindowWhenLimitReached() {
    UserEntity user = mock(UserEntity.class);
    TrackEntity track = trackWithWindows(3);

    when(user.getRank()).thenReturn(UserRank.NORMAL);

    assertThat(service.canCreateTrackWindowForTrack(user, track)).isFalse();
  }

  @Test
  void getLimitsForUserReturnsActualAndMaxValues() {
    UserEntity user = mock(UserEntity.class);

    TrackEntity track = trackWithWindows(3);
    BoardEntity board = new BoardEntity();
    board.setId(1L);
    SessionEntity session = new SessionEntity();
    session.setId(1L);
    session.setBoards(Set.of(board));

    when(user.getId()).thenReturn(1L);
    when(user.getRank()).thenReturn(UserRank.NORMAL);
    when(user.getOwnedTracks()).thenReturn(Set.of(track));
    when(user.getBoards()).thenReturn(rawSet(3));
    when(user.getOwnedGroups()).thenReturn(rawSet(2));
    when(user.getShares()).thenReturn(rawSet(10));


    when(sessionRepository.countByOwner_Id(1L)).thenReturn(5L);
    when(sessionRepository.findByOwner_Id(1L)).thenReturn(List.of(session));

    UserLimits result = service.getLimitsForUser(user);

    assertThat(result.getTracks().getActualTracks()).isEqualTo(1);
    assertThat(result.getTracks().getMaxTracks()).isEqualTo(10);
    assertThat(result.getTracks().getTrackLimitReached()).isFalse();

    assertThat(result.getBoards().getFirst().getActualBoards()).isEqualTo(1);
    assertThat(result.getBoards().getFirst().getMaxBoards()).isEqualTo(3);
    assertThat(result.getBoards().getFirst().getBoardLimitReached()).isFalse();

    assertThat(result.getGroups().getActualGroups()).isEqualTo(2);
    assertThat(result.getGroups().getMaxGroups()).isEqualTo(5);
    assertThat(result.getGroups().getGroupLimitReached()).isFalse();

    assertThat(result.getSubscribes().getActualSubscribes()).isEqualTo(10);
    assertThat(result.getSubscribes().getMaxSubscribes()).isEqualTo(10);
    assertThat(result.getSubscribes().getSubscribeLimitReached()).isTrue();

    assertThat(result.getSessions().getActualSessions()).isEqualTo(5);
    assertThat(result.getSessions().getMaxSessions()).isEqualTo(5);
    assertThat(result.getSessions().getSessionLimitReached()).isTrue();

    assertThat(result.getWindows().getFirst().getActualTrackWindows()).isEqualTo(3);
    assertThat(result.getWindows().getFirst().getMaxTrackWindows()).isEqualTo(3);
    assertThat(result.getWindows().getFirst().getTrackWindowsLimitReached()).isTrue();
  }

  private static Set<TrackEntity> tracks(int count) {
    Set<TrackEntity> tracks = new LinkedHashSet<>();

    for (int i = 0; i < count; i++) {
      tracks.add(mock(TrackEntity.class));
    }

    return tracks;
  }

  private static TrackEntity trackWithWindows(int windowCount) {
    TrackEntity track = mock(TrackEntity.class);
    when(track.getTrackWindows()).thenReturn(rawSet(windowCount));
    return track;
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static Set rawSet(int size) {
    Set set = new HashSet();

    for (int i = 0; i < size; i++) {
      set.add(new Object());
    }

    return set;
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static List rawList(int size) {
    return rawSet(size).stream().toList();
  }
}
