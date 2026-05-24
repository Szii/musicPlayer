package org.dnd.board;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dnd.api.model.Board;
import org.dnd.api.model.BoardCreateRequest;
import org.dnd.api.model.BoardUpdateRequest;
import org.dnd.exception.NotFoundException;
import org.dnd.group.GroupEntity;
import org.dnd.group.GroupRepository;
import org.dnd.session.SessionEntity;
import org.dnd.session.SessionRepository;
import org.dnd.track.TrackEntity;
import org.dnd.track.TrackRepository;
import org.dnd.user.UserEntity;
import org.dnd.user.UserRepository;
import org.dnd.utils.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class BoardService {
  private final BoardRepository boardRepository;
  private final UserRepository userRepository;
  private final TrackRepository trackRepository;
  private final GroupRepository groupRepository;
  private final BoardMapper boardMapper;
  private final BoardEnricher boardEnricher;
  private final SessionRepository sessionRepository;

  @Transactional(readOnly = true)
  public List<Board> getUserBoards() {
    Long userId = SecurityUtils.getCurrentUserId();

    log.debug("Getting boards for user with id {}", userId);

    return boardRepository.findByOwner_Id(userId).stream()
            .map(boardEntity -> toEnrichedDto(boardEntity, userId))
            .toList();
  }

  @Transactional(readOnly = true)
  public Board getUserBoard(Long boardId) {
    Long userId = SecurityUtils.getCurrentUserId();

    log.debug("Getting single board {} for user {}", boardId, userId);

    BoardEntity board = boardRepository.findByIdAndOwner_Id(boardId, userId)
            .orElseThrow(() -> new NotFoundException(
                    String.format("Board with id %d not found for user %d", boardId, userId)
            ));

    return toEnrichedDto(board, userId);
  }

  @Transactional
  public Long createUserBoard(BoardCreateRequest request) {
    Long userId = SecurityUtils.getCurrentUserId();

    log.debug("Creating board for user with id {}", userId);

    UserEntity owner = userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException(
                    String.format("User with id %d not found", userId)
            ));

    BoardEntity board = boardMapper.toEntity(request);
    board.setOwner(owner);

    SessionEntity session = sessionRepository.findByIdAndOwner_Id(request.getSessionId(), userId)
            .orElseThrow(() -> new NotFoundException(
                    String.format("Session with id %d not found for user %d", request.getSessionId(), userId)
            ));

    board.setSession(session);

    setTrackIfExist(request.getSelectedTrackId(), board);
    setGroupIfExist(request.getSelectedGroupId(), board);

    BoardEntity savedBoard = boardRepository.save(board);

    return savedBoard.getId();
  }

  @Transactional
  public Long deleteUserBoard(Long boardId) {
    Long userId = SecurityUtils.getCurrentUserId();
    log.debug("Deleting board {} for user {}", boardId, userId);
    BoardEntity board = boardRepository.findByIdAndOwner_Id(boardId, userId)
            .orElseThrow(() -> new NotFoundException(
                    String.format("Board with id %d not found for user %d", boardId, userId)
            ));
    boardRepository.delete(board);
    return board.getSession().getId();
  }

  @Transactional
  public Board updateUserBoard(Long boardId, BoardUpdateRequest request) {
    Long userId = SecurityUtils.getCurrentUserId();

    log.debug("Updating board {} for user {}", boardId, userId);

    BoardEntity board = boardRepository.findByIdAndOwner_Id(boardId, userId)
            .orElseThrow(() -> new NotFoundException(
                    String.format("Board with id %d not found for user %d", boardId, userId)
            ));

    boardMapper.updateBoardFromRequest(request, board);
    setTrackIfExist(request.getSelectedTrackId(), board);
    setGroupIfExist(request.getSelectedGroupId(), board);

    BoardEntity savedBoard = boardRepository.save(board);

    return toEnrichedDto(savedBoard, userId);
  }

  private void setTrackIfExist(Long selectedTrackId, BoardEntity board) {
    if (selectedTrackId == null) {
      board.setSelectedTrack(null);
      return;
    }

    TrackEntity track = trackRepository.findById(selectedTrackId)
            .orElseThrow(() -> new NotFoundException(String.format("Track with id %d not found", selectedTrackId)));
    board.setSelectedTrack(track);
  }

  private void setGroupIfExist(Long selectedGroupId, BoardEntity board) {
    if (selectedGroupId == null) {
      board.setSelectedGroup(null);
      return;
    }
    GroupEntity group = groupRepository.findById(selectedGroupId)
            .orElseThrow(() -> new NotFoundException(String.format("Group with id %d not found", selectedGroupId)));
    board.setSelectedGroup(group);

  }

  private Board toEnrichedDto(BoardEntity boardEntity, Long userId) {
    Board boardDto = boardMapper.toDto(boardEntity);
    return boardEnricher.enrich(boardDto, boardEntity, userId);
  }
}
