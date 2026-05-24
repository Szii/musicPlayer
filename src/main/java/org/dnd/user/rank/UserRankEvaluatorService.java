package org.dnd.user.rank;

import lombok.RequiredArgsConstructor;
import org.dnd.api.model.*;
import org.dnd.board.BoardEntity;
import org.dnd.session.SessionRepository;
import org.dnd.track.TrackEntity;
import org.dnd.user.UserEntity;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserRankEvaluatorService {

  private final SessionRepository sessionRepository;
  private final UserRankLimitProvider userRankLimitProvider;

  public boolean canCreateTrack(UserEntity user) {
    UserRankLimits limits = userRankLimitProvider.getLimits(user.getRank());
    return limits.canCreate(limits.maxTracks(), user.getOwnedTracks().size());
  }

  public boolean canCreateGroup(UserEntity user) {
    UserRankLimits limits = userRankLimitProvider.getLimits(user.getRank());
    return limits.canCreate(limits.maxGroups(), user.getOwnedGroups().size());
  }

  public boolean canCreateBoard(UserEntity user) {
    UserRankLimits limits = userRankLimitProvider.getLimits(user.getRank());
    return limits.canCreate(limits.maxBoards(), user.getBoards().size());
  }

  public boolean canCreateSession(UserEntity user) {
    UserRankLimits limits = userRankLimitProvider.getLimits(user.getRank());
    int actualSessions = sessionRepository.findByOwner_Id(user.getId()).size();

    return limits.canCreate(limits.maxSessions(), actualSessions);
  }

  public boolean canCreateTrackWindowForTrack(UserEntity user, TrackEntity track) {
    UserRankLimits limits = userRankLimitProvider.getLimits(user.getRank());
    return limits.canCreate(limits.maxWindows(), track.getTrackWindows().size());
  }

  public boolean canSubscribeToTrack(UserEntity user) {
    UserRankLimits limits = userRankLimitProvider.getLimits(user.getRank());
    return limits.canCreate(limits.maxShares(), user.getShares().size());
  }

  public UserLimits getLimitsForUser(UserEntity user) {
    UserRankLimits limits = userRankLimitProvider.getLimits(user.getRank());

    List<TrackEntity> userTracks = user.getOwnedTracks().stream().toList();
    List<BoardEntity> userBoards = user.getBoards().stream().toList();

    int actualTracks = userTracks.size();
    int actualBoards = userBoards.size();
    int actualGroups = user.getOwnedGroups().size();
    int actualShares = user.getShares().size();
    int actualSessions = Math.toIntExact(sessionRepository.countByOwner_Id(user.getId()));

    UserTracksLimits trackLimits = new UserTracksLimits();
    trackLimits.setActualTracks(actualTracks);
    trackLimits.setMaxTracks(limits.maxTracks());
    trackLimits.setTrackLimitReached(limits.isLimitReached(limits.maxTracks(), actualTracks));

    UserBoardsLimits boardLimits = new UserBoardsLimits();
    boardLimits.setActualBoards(actualBoards);
    boardLimits.setMaxBoards(limits.maxBoards());
    boardLimits.setBoardLimitReached(limits.isLimitReached(limits.maxBoards(), actualBoards));

    UserGroupsLimits groupLimits = new UserGroupsLimits();
    groupLimits.setActualGroups(actualGroups);
    groupLimits.setMaxGroups(limits.maxGroups());
    groupLimits.setGroupLimitReached(limits.isLimitReached(limits.maxGroups(), actualGroups));

    UserSharesLimit subscriptionLimits = new UserSharesLimit();
    subscriptionLimits.actualSubscribes(actualShares);
    subscriptionLimits.maxSubscribes(limits.maxShares());
    subscriptionLimits.subscribeLimitReached(limits.isLimitReached(limits.maxShares(), actualShares));

    UserSessionsLimit sessionLimits = new UserSessionsLimit();
    sessionLimits.setActualSessions(actualSessions);
    sessionLimits.setMaxSessions(limits.maxSessions());
    sessionLimits.setSessionLimitReached(limits.isLimitReached(limits.maxSessions(), actualSessions));

    List<UserWindowsLimit> trackWindowsLimits = userTracks.stream()
            .map(track -> {
              int actualWindows = track.getTrackWindows().size();

              UserWindowsLimit windowLimits = new UserWindowsLimit();
              windowLimits.setActualTrackWindows(actualWindows);
              windowLimits.setMaxTrackWindows(limits.maxWindows());
              windowLimits.setTrackWindowsLimitReached(
                      limits.isLimitReached(limits.maxWindows(), actualWindows)
              );

              return windowLimits;
            })
            .toList();

    return new UserLimits()
            .tracks(trackLimits)
            .groups(groupLimits)
            .boards(boardLimits)
            .sessions(sessionLimits)
            .subscribes(subscriptionLimits)
            .windows(trackWindowsLimits);
  }

}
