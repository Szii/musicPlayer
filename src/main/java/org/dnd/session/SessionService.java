package org.dnd.session;

import lombok.RequiredArgsConstructor;
import org.dnd.api.model.SessionRequest;
import org.dnd.api.model.SessionResponse;
import org.dnd.api.model.SessionsResponse;
import org.dnd.board.BoardEnricher;
import org.dnd.board.BoardEntity;
import org.dnd.exception.LimitReachedException;
import org.dnd.exception.NotFoundException;
import org.dnd.group.GroupEntity;
import org.dnd.group.GroupRepository;
import org.dnd.track.TrackEntity;
import org.dnd.track.TrackRepository;
import org.dnd.user.UserEntity;
import org.dnd.user.UserRepository;
import org.dnd.user.rank.UserRankEvaluatorService;
import org.dnd.utils.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
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

    SessionEntity existingSession = findOwnedSession(sessionRequest.getSessionId(), userId);

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
    SessionEntity sessionEntity = findOwnedSession(sessionId, userId);

    TrackEntity track = trackRepository.findAccessibleByIdAndUserId(trackId, userId)
            .orElseThrow(() -> new NotFoundException(String.format("Track with id %s not found", trackId)));

    sessionEntity.getTracks().add(track);
    return toEnrichedResponse(sessionEntity, userId);
  }

  @Transactional
  public SessionResponse removeTrack(UUID sessionId, UUID trackId) {
    UUID userId = securityUtils.getCurrentUserId();
    SessionEntity sessionEntity = findOwnedSession(sessionId, userId);

    sessionEntity.getTracks().removeIf(track -> track.getId().equals(trackId));
    return toEnrichedResponse(sessionEntity, userId);
  }

  @Transactional
  public SessionResponse addGroup(UUID sessionId, UUID groupId) {
    UUID userId = securityUtils.getCurrentUserId();
    SessionEntity sessionEntity = findOwnedSession(sessionId, userId);

    GroupEntity group = groupRepository.findByIdAndOwner_Id(groupId, userId)
            .orElseThrow(() -> new NotFoundException(String.format("Group with id %s not found", groupId)));

    sessionEntity.getGroups().add(group);
    return toEnrichedResponse(sessionEntity, userId);
  }

  @Transactional
  public SessionResponse removeGroup(UUID sessionId, UUID groupId) {
    UUID userId = securityUtils.getCurrentUserId();
    SessionEntity sessionEntity = findOwnedSession(sessionId, userId);

    sessionEntity.getGroups().removeIf(group -> group.getId().equals(groupId));
    return toEnrichedResponse(sessionEntity, userId);
  }

  @Transactional
  public void attachTrack(UUID sessionId, TrackEntity track) {
    if (sessionId == null) {
      return;
    }
    findOwnedSession(sessionId, securityUtils.getCurrentUserId()).getTracks().add(track);
  }

  @Transactional
  public void attachGroup(UUID sessionId, GroupEntity group) {
    if (sessionId == null) {
      return;
    }
    findOwnedSession(sessionId, securityUtils.getCurrentUserId()).getGroups().add(group);
  }

  private SessionEntity findOwnedSession(UUID sessionId, UUID userId) {
    return sessionRepository.findByIdAndOwner_Id(sessionId, userId)
            .orElseThrow(() -> new NotFoundException(
                    String.format("Session with id %s not found for user %s", sessionId, userId)
            ));
  }

  private SessionResponse toEnrichedResponse(SessionEntity sessionEntity, UUID userId) {
    SessionResponse response = sessionMapper.toResponse(sessionEntity);
    enrichSessionWithBoards(response, sessionEntity, userId);
    return response;
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
