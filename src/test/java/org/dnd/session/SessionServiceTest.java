package org.dnd.session;

import org.dnd.api.model.Board;
import org.dnd.api.model.SessionRequest;
import org.dnd.api.model.SessionResponse;
import org.dnd.api.model.SessionsResponse;
import org.dnd.board.BoardEnricher;
import org.dnd.board.BoardEntity;
import org.dnd.exception.NotFoundException;
import org.dnd.user.UserEntity;
import org.dnd.user.UserRepository;
import org.dnd.user.rank.UserRankEvaluatorService;
import org.dnd.utils.SecurityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

  @Mock
  private SessionMapper sessionMapper;

  @Mock
  private SessionRepository sessionRepository;

  @Mock
  private BoardEnricher boardEnricher;

  @Mock
  private UserRepository userRepository;

  @Mock
  private UserRankEvaluatorService userRankEvaluatorService;

  @InjectMocks
  private SessionService sessionService;

  @BeforeEach
  void setUp() {
    lenient().when(userRankEvaluatorService.canCreateSession(any())).thenReturn(true);
  }
  
  @Test
  void getSession_whenNotFound_throwsNotFoundException() {
    Long userId = 1L;
    Long sessionId = 99L;

    when(sessionRepository.findByIdAndOwner_Id(sessionId, userId))
            .thenReturn(Optional.empty());

    try (MockedStatic<SecurityUtils> securityUtils = mockStatic(SecurityUtils.class)) {
      securityUtils.when(SecurityUtils::getCurrentUserId).thenReturn(userId);

      assertThrows(NotFoundException.class, () -> sessionService.getSession(sessionId));

      verify(sessionRepository).findByIdAndOwner_Id(sessionId, userId);
    }
  }

  @Test
  void createSession_savesSessionForCurrentUser() {
    Long userId = 1L;

    UserEntity user = new UserEntity();
    user.setId(userId);
    user.setName("testUser");

    SessionRequest request = new SessionRequest()
            .sessionName("New Session")
            .sessionDescription("Description");

    when(userRepository.findById(userId))
            .thenReturn(Optional.of(user));

    when(sessionRepository.findByOwner_Id(userId))
            .thenReturn(List.of());

    try (MockedStatic<SecurityUtils> securityUtils = mockStatic(SecurityUtils.class)) {
      securityUtils.when(SecurityUtils::getCurrentUserId).thenReturn(userId);

      sessionService.createSession(request);

      verify(sessionRepository).save(argThat(session ->
              session.getOwner().equals(user)
                      && session.getName().equals("New Session")
                      && session.getDescription().equals("Description")
      ));
    }
  }

  @Test
  void updateSession_updatesExistingSession() {
    Long userId = 1L;
    Long sessionId = 10L;

    SessionEntity existingSession = new SessionEntity();
    existingSession.setId(sessionId);
    existingSession.setName("Old Name");
    existingSession.setDescription("Old Description");

    SessionRequest request = new SessionRequest()
            .sessionId(sessionId)
            .sessionName("Updated Name")
            .sessionDescription("Updated Description");

    when(sessionRepository.findByIdAndOwner_Id(sessionId, userId))
            .thenReturn(Optional.of(existingSession));

    when(sessionRepository.findByOwner_Id(userId))
            .thenReturn(List.of(existingSession));

    SessionResponse mappedResponse = new SessionResponse();
    mappedResponse.setSessionId(sessionId);
    mappedResponse.setSessionName("Updated Name");
    mappedResponse.setSessionDescription("Updated Description");
    mappedResponse.setBoards(List.of());

    when(sessionMapper.toResponse(existingSession))
            .thenReturn(mappedResponse);

    try (MockedStatic<SecurityUtils> securityUtils = mockStatic(SecurityUtils.class)) {
      securityUtils.when(SecurityUtils::getCurrentUserId).thenReturn(userId);

      SessionsResponse response = sessionService.updateSession(request);

      assertEquals("Updated Name", existingSession.getName());
      assertEquals("Updated Description", existingSession.getDescription());
      assertEquals(1, response.getSessions().size());

      verify(sessionRepository).save(existingSession);
    }
  }

  @Test
  void deleteSession_deletesSession() {
    Long userId = 1L;
    Long sessionId = 10L;

    SessionEntity session = new SessionEntity();
    session.setId(sessionId);

    when(sessionRepository.findByIdAndOwner_Id(sessionId, userId))
            .thenReturn(Optional.of(session));

    when(sessionRepository.findByOwner_Id(userId))
            .thenReturn(List.of());

    try (MockedStatic<SecurityUtils> securityUtils = mockStatic(SecurityUtils.class)) {
      securityUtils.when(SecurityUtils::getCurrentUserId).thenReturn(userId);

      SessionsResponse response = sessionService.deleteSession(sessionId);

      assertTrue(response.getSessions().isEmpty());

      verify(sessionRepository).delete(session);
    }
  }

  @Test
  void getSession_enrichesBoards() {
    Long userId = 1L;
    Long sessionId = 10L;
    Long boardId = 100L;

    BoardEntity boardEntity = new BoardEntity();
    boardEntity.setId(boardId);

    SessionEntity sessionEntity = new SessionEntity();
    sessionEntity.setId(sessionId);
    sessionEntity.setBoards(Set.of(boardEntity));

    Board boardDto = new Board();
    boardDto.setId(boardId);

    SessionResponse sessionResponse = new SessionResponse();
    sessionResponse.setSessionId(sessionId);
    sessionResponse.setBoards(List.of(boardDto));

    when(sessionRepository.findByIdAndOwner_Id(sessionId, userId))
            .thenReturn(Optional.of(sessionEntity));

    when(sessionMapper.toResponse(sessionEntity))
            .thenReturn(sessionResponse);

    when(boardEnricher.enrich(boardDto, boardEntity, userId))
            .thenReturn(boardDto);

    try (MockedStatic<SecurityUtils> securityUtils = mockStatic(SecurityUtils.class)) {
      securityUtils.when(SecurityUtils::getCurrentUserId).thenReturn(userId);

      SessionResponse response = sessionService.getSession(sessionId);

      assertEquals(sessionId, response.getSessionId());

      verify(boardEnricher).enrich(boardDto, boardEntity, userId);
    }
  }
}
