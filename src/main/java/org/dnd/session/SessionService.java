package org.dnd.session;

import lombok.RequiredArgsConstructor;
import org.dnd.api.model.SessionRequest;
import org.dnd.api.model.SessionResponse;
import org.dnd.api.model.SessionsResponse;
import org.dnd.board.BoardEnricher;
import org.dnd.board.BoardEntity;
import org.dnd.exception.LimitReachedException;
import org.dnd.exception.NotFoundException;
import org.dnd.user.UserEntity;
import org.dnd.user.UserRepository;
import org.dnd.user.rank.UserRankEvaluatorService;
import org.dnd.utils.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SessionService {

  private final SessionMapper sessionMapper;
  private final SessionRepository sessionRepository;
  private final BoardEnricher boardEnricher;
  private final UserRepository userRepository;
  private final UserRankEvaluatorService userRankEvaluatorService;

  @Transactional(readOnly = true)
  public SessionsResponse getSessions() {
    Long userId = SecurityUtils.getCurrentUserId();

    List<SessionResponse> sessionResponses = sessionRepository.findByOwner_Id(userId).stream()
            .map(sessionEntity -> toEnrichedResponse(sessionEntity, userId))
            .toList();

    SessionsResponse response = new SessionsResponse();
    response.setSessions(sessionResponses);
    return response;
  }

  @Transactional
  public SessionsResponse deleteSession(Long sessionId) {
    Long userId = SecurityUtils.getCurrentUserId();

    SessionEntity sessionEntity = sessionRepository.findByIdAndOwner_Id(sessionId, userId)
            .orElseThrow(() -> new NotFoundException(
                    String.format("Session with id %d not found for user %d", sessionId, userId)
            ));

    sessionRepository.delete(sessionEntity);
    return getSessions();
  }

  @Transactional
  public SessionsResponse createSession(SessionRequest sessionRequest) {
    Long userId = SecurityUtils.getCurrentUserId();
    SessionEntity sessionEntity = new SessionEntity();

    UserEntity user = userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException(String.format("User with id %d not found", userId)));

    if (!userRankEvaluatorService.canCreateSession(user)) {
      throw new LimitReachedException("Session limit reached");
    }

    sessionEntity.setOwner(user);
    sessionEntity.setName(sessionRequest.getSessionName());
    sessionEntity.setDescription(sessionRequest.getSessionDescription());

    sessionRepository.save(sessionEntity);

    return getSessions();
  }

  public SessionsResponse updateSession(SessionRequest sessionRequest) {
    Long userId = SecurityUtils.getCurrentUserId();

    SessionEntity existingSession = sessionRepository.findByIdAndOwner_Id(sessionRequest.getSessionId(), userId)
            .orElseThrow(() -> new NotFoundException(
                    String.format("Session with id %d not found for user %d", sessionRequest.getSessionId(), userId)
            ));

    existingSession.setName(sessionRequest.getSessionName());
    existingSession.setDescription(sessionRequest.getSessionDescription());

    sessionRepository.save(existingSession);

    return getSessions();
  }

  @Transactional(readOnly = true)
  public SessionResponse getSession(Long sessionId) {
    Long userId = SecurityUtils.getCurrentUserId();

    SessionEntity sessionEntity = sessionRepository.findByIdAndOwner_Id(sessionId, userId)
            .orElseThrow(() -> new NotFoundException(
                    String.format("Session with id %d not found for user %d", sessionId, userId)
            ));

    return toEnrichedResponse(sessionEntity, userId);
  }

  private SessionResponse toEnrichedResponse(SessionEntity sessionEntity, Long userId) {
    SessionResponse response = sessionMapper.toResponse(sessionEntity);
    enrichSessionWithBoards(response, sessionEntity, userId);
    return response;
  }

  private void enrichSessionWithBoards(SessionResponse sessionResponse, SessionEntity sessionEntity, Long userId) {
    if (sessionResponse.getBoards() == null || sessionEntity.getBoards() == null) {
      return;
    }

    Map<Long, BoardEntity> boardEntitiesById = sessionEntity.getBoards().stream()
            .collect(Collectors.toMap(BoardEntity::getId, Function.identity()));

    sessionResponse.getBoards().forEach(boardDto -> {
      BoardEntity boardEntity = boardEntitiesById.get(boardDto.getId());

      if (boardEntity != null) {
        boardEnricher.enrich(boardDto, boardEntity, userId);
      }
    });
  }
}
