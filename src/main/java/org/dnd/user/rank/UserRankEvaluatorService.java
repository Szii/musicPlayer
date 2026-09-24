package org.dnd.user.rank;

import lombok.RequiredArgsConstructor;
import org.dnd.api.model.*;
import org.dnd.session.SessionEntity;
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
    return limits.canCreate(limits.maxTracks(), ownTracks(user).size());
  }

  public boolean canCreateGroup(UserEntity user) {
    UserRankLimits limits = userRankLimitProvider.getLimits(user.getRank());
    return limits.canCreate(limits.maxGroups(), ownGroupCount(user));
  }

  public boolean canCreateBoardForSession(UserEntity user, SessionEntity session) {
    UserRankLimits limits = userRankLimitProvider.getLimits(user.getRank());
    return limits.canCreate(limits.maxBoards(), session.getBoards().size());
  }

  public boolean canCreateSession(UserEntity user) {
    UserRankLimits limits = userRankLimitProvider.getLimits(user.getRank());
    int actualSessions = Math.toIntExact(sessionRepository.countByOwner_IdAndSubscribed(user.getId(), false));

    return limits.canCreate(limits.maxSessions(), actualSessions);
  }

  public boolean canCreateTrackWindowForTrack(UserEntity user, TrackEntity track) {
    UserRankLimits limits = userRankLimitProvider.getLimits(user.getRank());
    return limits.canCreate(limits.maxWindows(), track.getTrackWindows().size());
  }

  public boolean canSubscribeToSession(UserEntity user) {
    UserRankLimits limits = userRankLimitProvider.getLimits(user.getRank());
    return limits.canCreate(limits.maxShares(), subscribedSessionCount(user));
  }

  public UserLimits getLimitsForUser(UserEntity user) {
    UserRankLimits limits = userRankLimitProvider.getLimits(user.getRank());

    List<TrackEntity> userTracks = ownTracks(user);
    List<SessionEntity> userSessions = sessionRepository.findByOwner_IdAndSubscribed(user.getId(), false);

    int actualTracks = userTracks.size();
    int actualGroups = ownGroupCount(user);
    int actualShares = subscribedSessionCount(user);
    int actualSessions = Math.toIntExact(sessionRepository.countByOwner_IdAndSubscribed(user.getId(), false));

    UserTracksLimits trackLimits = new UserTracksLimits();
    trackLimits.setActualTracks(actualTracks);
    trackLimits.setMaxTracks(limits.maxTracks());
    trackLimits.setTrackLimitReached(limits.isLimitReached(limits.maxTracks(), actualTracks));

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
              windowLimits.setTrackId(track.getId());
              windowLimits.setTrackWindowsLimitReached(
                      limits.isLimitReached(limits.maxWindows(), actualWindows)
              );

              return windowLimits;
            })
            .toList();

    List<UserBoardsLimits> boardLimitsPerSession = userSessions.stream()
            .map(session -> {
              int actualBoards = session.getBoards().size();
              UserBoardsLimits boardLimits = new UserBoardsLimits();
              boardLimits.setActualBoards(actualBoards);
              boardLimits.setMaxBoards(limits.maxBoards());
              boardLimits.setBoardLimitReached(limits.isLimitReached(limits.maxBoards(), actualBoards));
              boardLimits.setSessionId(session.getId());
              return boardLimits;
            }).toList();

    return new UserLimits()
            .level(UserRankLevel.valueOf(user.getRank().name()))
            .tracks(trackLimits)
            .groups(groupLimits)
            .boards(boardLimitsPerSession)
            .sessions(sessionLimits)
            .subscribes(subscriptionLimits)
            .windows(trackWindowsLimits);
  }

  private List<TrackEntity> ownTracks(UserEntity user) {
    return user.getOwnedTracks().stream()
            .filter(track -> !track.isManaged())
            .toList();
  }

  private int ownGroupCount(UserEntity user) {
    return Math.toIntExact(user.getOwnedGroups().stream()
            .filter(group -> !group.isManaged())
            .count());
  }

  private int subscribedSessionCount(UserEntity user) {
    return Math.toIntExact(sessionRepository.countByOwner_IdAndSubscribed(user.getId(), true));
  }

}
