package org.dnd.session;

import lombok.RequiredArgsConstructor;
import org.dnd.api.model.Board;
import org.dnd.api.model.ReorderSessionBoardsRequest;
import org.dnd.api.model.SessionPublication;
import org.dnd.api.model.SessionRequest;
import org.dnd.api.model.SessionResponse;
import org.dnd.api.model.SessionSubscription;
import org.dnd.api.model.SessionsResponse;
import org.dnd.board.BoardEnricher;
import org.dnd.board.BoardEntity;
import org.dnd.exception.BadRequestException;
import org.dnd.exception.ForbiddenException;
import org.dnd.exception.LimitReachedException;
import org.dnd.exception.NotFoundException;
import org.dnd.group.GroupEntity;
import org.dnd.group.GroupMapper;
import org.dnd.group.GroupRepository;
import org.dnd.session.share.SessionShareEntity;
import org.dnd.session.share.SessionShareLifecycle;
import org.dnd.session.share.SessionShareRepository;
import org.dnd.track.TrackEntity;
import org.dnd.track.TrackMapper;
import org.dnd.track.TrackRepository;
import org.dnd.user.UserEntity;
import org.dnd.user.UserMapper;
import org.dnd.user.UserRepository;
import org.dnd.user.rank.UserRankEvaluatorService;
import org.dnd.utils.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SessionService {

  private final SessionMapper sessionMapper;
  private final SessionRepository sessionRepository;
  private final TrackRepository trackRepository;
  private final GroupRepository groupRepository;
  private final BoardEnricher boardEnricher;
  private final UserRepository userRepository;
  private final UserRankEvaluatorService userRankEvaluatorService;
  private final SecurityUtils securityUtils;
  private final SessionShareRepository sessionShareRepository;
  private final SessionShareLifecycle sessionShareLifecycle;
  private final TrackMapper trackMapper;
  private final GroupMapper groupMapper;
  private final UserMapper userMapper;

  @Transactional(readOnly = true)
  public SessionsResponse getSessions() {
    UUID userId = securityUtils.getCurrentUserId();

    List<SessionResponse> sessionResponses = sessionRepository.findByOwner_Id(userId).stream()
            .map(sessionEntity -> toEnrichedResponse(sessionEntity, userId))
            .toList();

    SessionsResponse response = new SessionsResponse();
    response.setSessions(sessionResponses);
    return response;
  }

  @Transactional
  public SessionsResponse deleteSession(UUID sessionId) {
    UUID userId = securityUtils.getCurrentUserId();

    SessionEntity sessionEntity = findOwnedSession(sessionId, userId);

    sessionShareLifecycle.beforeSessionDelete(sessionEntity);
    sessionRepository.delete(sessionEntity);
    return getSessions();
  }

  @Transactional
  public SessionsResponse createSession(SessionRequest sessionRequest) {
    UUID userId = securityUtils.getCurrentUserId();
    SessionEntity sessionEntity = new SessionEntity();

    UserEntity user = userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException(String.format("User with id %s not found", userId)));

    if (!userRankEvaluatorService.canCreateSession(user)) {
      throw new LimitReachedException("Session limit reached");
    }

    sessionEntity.setOwner(user);
    sessionEntity.setName(sessionRequest.getSessionName());
    sessionEntity.setDescription(sessionRequest.getSessionDescription());

    sessionRepository.save(sessionEntity);

    return getSessions();
  }

  @Transactional
  public SessionsResponse updateSession(SessionRequest sessionRequest) {
    UUID userId = securityUtils.getCurrentUserId();

    SessionEntity existingSession = findEditableSession(sessionRequest.getSessionId(), userId);

    existingSession.setName(sessionRequest.getSessionName());
    existingSession.setDescription(sessionRequest.getSessionDescription());

    sessionRepository.save(existingSession);

    return getSessions();
  }

  @Transactional(readOnly = true)
  public SessionResponse getSession(UUID sessionId) {
    UUID userId = securityUtils.getCurrentUserId();

    SessionEntity sessionEntity = findOwnedSession(sessionId, userId);

    return toEnrichedResponse(sessionEntity, userId);
  }

  @Transactional
  public SessionResponse addTrack(UUID sessionId, UUID trackId) {
    UUID userId = securityUtils.getCurrentUserId();
    SessionEntity sessionEntity = findEditableSession(sessionId, userId);

    TrackEntity track = trackRepository.findAccessibleByIdAndUserId(trackId, userId)
            .orElseThrow(() -> new NotFoundException(String.format("Track with id %s not found", trackId)));

    sessionEntity.getTracks().add(track);
    return toEnrichedResponse(sessionEntity, userId);
  }

  @Transactional
  public SessionResponse removeTrack(UUID sessionId, UUID trackId) {
    UUID userId = securityUtils.getCurrentUserId();
    SessionEntity sessionEntity = findEditableSession(sessionId, userId);

    sessionEntity.getTracks().removeIf(track -> track.getId().equals(trackId));
    clearSelectionsOutsideSession(sessionEntity);
    return toEnrichedResponse(sessionEntity, userId);
  }

  @Transactional
  public SessionResponse addGroup(UUID sessionId, UUID groupId) {
    UUID userId = securityUtils.getCurrentUserId();
    SessionEntity sessionEntity = findEditableSession(sessionId, userId);

    GroupEntity group = groupRepository.findByIdAndOwner_IdAndManagedSessionIsNull(groupId, userId)
            .orElseThrow(() -> new NotFoundException(String.format("Group with id %s not found", groupId)));

    sessionEntity.getGroups().add(group);
    return toEnrichedResponse(sessionEntity, userId);
  }

  @Transactional
  public SessionResponse removeGroup(UUID sessionId, UUID groupId) {
    UUID userId = securityUtils.getCurrentUserId();
    SessionEntity sessionEntity = findEditableSession(sessionId, userId);

    sessionEntity.getGroups().removeIf(group -> group.getId().equals(groupId));
    clearSelectionsOutsideSession(sessionEntity);
    return toEnrichedResponse(sessionEntity, userId);
  }

  @Transactional
  public SessionResponse reorderBoards(UUID sessionId, ReorderSessionBoardsRequest request) {
    UUID userId = securityUtils.getCurrentUserId();
    SessionEntity sessionEntity = findOwnedSession(sessionId, userId);

    List<UUID> boardIds = request.getBoardIds();
    if (boardIds == null || boardIds.isEmpty()) {
      throw new BadRequestException("Board ids must not be empty");
    }
    if (boardIds.size() != new HashSet<>(boardIds).size()) {
      throw new BadRequestException("Board ids must not contain duplicates");
    }

    Map<UUID, BoardEntity> boardsById = sessionEntity.getBoards().stream()
            .collect(Collectors.toMap(BoardEntity::getId, Function.identity()));
    if (boardIds.size() != boardsById.size()) {
      throw new BadRequestException("Request must contain all boards of the session");
    }

    boolean changed = false;
    for (int i = 0; i < boardIds.size(); i++) {
      BoardEntity board = boardsById.get(boardIds.get(i));
      if (board == null) {
        throw new BadRequestException(String.format("Board %s does not belong to session %s", boardIds.get(i), sessionId));
      }
      if (board.getPositionWithinSession() != i + 1) {
        board.setPositionWithinSession(i + 1);
        changed = true;
      }
    }

    if (changed && sessionEntity.isSubscribed()) {
      sessionEntity.setModified(true);
    }

    return toEnrichedResponse(sessionEntity, userId);
  }

  @Transactional
  public void attachTrack(UUID sessionId, TrackEntity track) {
    if (sessionId == null) {
      return;
    }
    findEditableSession(sessionId, securityUtils.getCurrentUserId()).getTracks().add(track);
  }

  @Transactional
  public void attachGroup(UUID sessionId, GroupEntity group) {
    if (sessionId == null) {
      return;
    }
    findEditableSession(sessionId, securityUtils.getCurrentUserId()).getGroups().add(group);
  }

  public SessionResponse toEnrichedResponse(SessionEntity sessionEntity, UUID userId) {
    SessionResponse response = sessionMapper.toResponse(sessionEntity);
    if (response.getBoards() != null) {
      response.setBoards(response.getBoards().stream()
              .sorted(Comparator.comparing(Board::getPosition).thenComparing(Board::getName))
              .collect(Collectors.toCollection(ArrayList::new)));
    }
    enrichSessionWithBoards(response, sessionEntity, userId);
    response.setReadOnly(sessionEntity.isSubscribed());
    response.setTrackCount(allTracks(sessionEntity).size());
    if (sessionEntity.isSubscribed()) {
      response.setSubscription(toSubscription(sessionEntity, userId));
    } else {
      sessionShareRepository.findBySession_IdAndUnpublishedAtIsNull(sessionEntity.getId())
              .ifPresent(share -> response.setPublication(toPublication(share)));
    }
    return response;
  }

  public static OffsetDateTime toOffset(LocalDateTime dateTime) {
    return dateTime == null ? null : dateTime.atZone(ZoneId.systemDefault()).toOffsetDateTime();
  }

  private void clearSelectionsOutsideSession(SessionEntity session) {
    if (session.getBoards() == null) {
      return;
    }

    Set<UUID> groupIds = session.getGroups().stream()
            .map(GroupEntity::getId)
            .collect(Collectors.toSet());
    Set<UUID> trackIds = session.getTracks().stream()
            .map(TrackEntity::getId)
            .collect(Collectors.toCollection(HashSet::new));
    session.getGroups().forEach(group ->
            group.getGroupTracks().forEach(groupTrack -> trackIds.add(groupTrack.getTrack().getId())));

    for (BoardEntity board : session.getBoards()) {
      if (board.getSelectedGroup() != null && !groupIds.contains(board.getSelectedGroup().getId())) {
        board.setSelectedGroup(null);
      }
      if (board.getSelectedTrack() != null && !trackIds.contains(board.getSelectedTrack().getId())) {
        board.setSelectedTrack(null);
        board.setSelectedWindow(null);
      }
    }
  }

  private SessionEntity findEditableSession(UUID sessionId, UUID userId) {
    SessionEntity session = findOwnedSession(sessionId, userId);
    if (session.isSubscribed()) {
      throw new ForbiddenException("Subscribed sessions cannot be changed");
    }
    return session;
  }

  private SessionEntity findOwnedSession(UUID sessionId, UUID userId) {
    return sessionRepository.findByIdAndOwner_Id(sessionId, userId)
            .orElseThrow(() -> new NotFoundException(
                    String.format("Session with id %s not found for user %s", sessionId, userId)
            ));
  }

  private SessionSubscription toSubscription(SessionEntity session, UUID userId) {
    SessionShareEntity share = session.getSourceShare();
    int installedVersion = session.getInstalledVersion() == null ? 0 : session.getInstalledVersion();

    SessionSubscription subscription = new SessionSubscription()
            .installedVersion(installedVersion)
            .modified(session.isModified())
            .tracks(allTracks(session).stream().map(track -> trackMapper.toDto(track, userId)).toList())
            .groups(groupMapper.toDtos(List.copyOf(session.getGroups())));

    if (share == null) {
      return subscription
              .updateAvailable(false)
              .unpublished(true)
              .restorable(false);
    }

    return subscription
            .shareId(share.getId())
            .owner(userMapper.toLiteUserDto(share.getOwner()))
            .latestVersion(share.getVersion())
            .updateAvailable(share.isPublished() && share.getVersion() > installedVersion)
            .unpublished(!share.isPublished())
            .restorable(true);
  }

  private Set<TrackEntity> allTracks(SessionEntity session) {
    Set<TrackEntity> tracks = new LinkedHashSet<>(session.getTracks());
    session.getGroups().forEach(group ->
            group.getGroupTracks().forEach(groupTrack -> tracks.add(groupTrack.getTrack())));
    return tracks;
  }

  private SessionPublication toPublication(SessionShareEntity share) {
    return new SessionPublication()
            .shareId(share.getId())
            .shareCode(share.getShareCode())
            .description(share.getDescription())
            .version(share.getVersion())
            .publishedAt(toOffset(share.getPublishedAt()))
            .updatedAt(toOffset(share.getUpdatedAt()))
            .subscriberCount(Math.toIntExact(sessionRepository.countBySourceShare_Id(share.getId())));
  }

  private void enrichSessionWithBoards(SessionResponse sessionResponse, SessionEntity sessionEntity, UUID userId) {
    if (sessionResponse.getBoards() == null || sessionEntity.getBoards() == null) {
      return;
    }

    Map<UUID, BoardEntity> boardEntitiesById = sessionEntity.getBoards().stream()
            .collect(Collectors.toMap(BoardEntity::getId, Function.identity()));

    sessionResponse.getBoards().forEach(boardDto -> {
      BoardEntity boardEntity = boardEntitiesById.get(boardDto.getId());

      if (boardEntity != null) {
        boardEnricher.enrich(boardDto, boardEntity, userId);
      }
    });
  }
}
