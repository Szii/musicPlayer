package org.dnd.board;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dnd.api.model.Board;
import org.dnd.api.model.BoardCreateRequest;
import org.dnd.api.model.BoardUpdateRequest;
import org.dnd.exception.BadRequestException;
import org.dnd.exception.ForbiddenException;
import org.dnd.exception.LimitReachedException;
import org.dnd.exception.NotFoundException;
import org.dnd.group.GroupEntity;
import org.dnd.group.GroupRepository;
import org.dnd.session.SessionEntity;
import org.dnd.session.SessionRepository;
import org.dnd.track.TrackEntity;
import org.dnd.track.TrackRepository;
import org.dnd.track.TrackWindowEntity;
import org.dnd.track.TrackWindowRepository;
import org.dnd.user.UserEntity;
import org.dnd.user.UserRepository;
import org.dnd.user.rank.UserRankEvaluatorService;
import org.dnd.utils.SecurityUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

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
  private final UserRankEvaluatorService userRankEvaluatorService;
  private final TrackWindowRepository trackWindowRepository;
  private final SecurityUtils securityUtils;

  @Transactional(readOnly = true)
  public List<Board> getUserBoards() {
    UUID userId = securityUtils.getCurrentUserId();

    log.debug("Getting boards for user with id {}", userId);

    return boardRepository.findByOwner_Id(userId).stream()
            .map(boardEntity -> toEnrichedDto(boardEntity, userId))
            .toList();
  }

  @Transactional(readOnly = true)
  public Board getUserBoard(UUID boardId) {
    UUID userId = securityUtils.getCurrentUserId();

    log.debug("Getting single board {} for user {}", boardId, userId);

    BoardEntity board = boardRepository.findByIdAndOwner_Id(boardId, userId)
            .orElseThrow(() -> new NotFoundException(
                    String.format("Board with id %s not found for user %s", boardId, userId)
            ));

    return toEnrichedDto(board, userId);
  }

  @Transactional
  public UUID createUserBoard(BoardCreateRequest request) {
    UUID userId = securityUtils.getCurrentUserId();

    log.debug("Creating board for user with id {}", userId);

    UserEntity owner = userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException(
                    String.format("User with id %s not found", userId)
            ));

    SessionEntity session = sessionRepository.findByIdAndOwner_Id(request.getSessionId(), userId)
            .orElseThrow(() -> new NotFoundException(
                    String.format("Session with id %s not found for user %s", request.getSessionId(), userId)
            ));

    requireEditable(session);

    if (!userRankEvaluatorService.canCreateBoardForSession(owner, session)) {
      throw new LimitReachedException("Board limit reached");
    }

    BoardEntity board = boardMapper.toEntity(request);
    board.setOwner(owner);

    board.setSession(session);
    board.setPositionWithinSession(session.getBoards().stream()
            .mapToInt(BoardEntity::getPositionWithinSession)
            .max()
            .orElse(0) + 1);

    setGroupIfExist(request.getSelectedGroupId(), board);
    setTrackIfExist(request.getSelectedTrackId(), board);

    session.getBoards().add(board);

    BoardEntity savedBoard = boardRepository.save(board);

    return savedBoard.getSession().getId();
  }

  @Transactional
  public UUID deleteUserBoard(UUID boardId) {
    UUID userId = securityUtils.getCurrentUserId();
    log.debug("Deleting board {} for user {}", boardId, userId);
    BoardEntity board = boardRepository.findByIdAndOwner_Id(boardId, userId)
            .orElseThrow(() -> new NotFoundException(
                    String.format("Board with id %s not found for user %s", boardId, userId)
            ));
    requireEditable(board.getSession());
    boardRepository.delete(board);
    return board.getSession().getId();
  }

  @PreAuthorize("#request.linkedBoard == null or @resourceAccess.isBoardOwner(#request.linkedBoard.boardId)")
  @Transactional
  public Board updateUserBoard(UUID boardId, BoardUpdateRequest request) {
    UUID userId = securityUtils.getCurrentUserId();
    log.debug("Updating board {} for user {}", boardId, userId);

    BoardEntity board = boardRepository.findByIdAndOwner_Id(boardId, userId)
            .orElseThrow(() -> new NotFoundException(
                    String.format("Board with id %s not found for user %s", boardId, userId)
            ));

    if (board.getSession().isSubscribed()) {
      applyPlaybackUpdate(board, request);
      return toEnrichedDto(boardRepository.save(board), userId);
    }

    boardMapper.updateBoardFromRequest(request, board);
    applyRepeatGap(board, request);
    setGroupIfExist(request.getSelectedGroupId(), board);
    setTrackIfExist(request.getSelectedTrackId(), board);
    setWindowIfExist(request.getSelectedWindowId(), board);
    board.setLinkedBoard(boardMapper.toLinkedBoard(request.getLinkedBoard()));

    BoardEntity savedBoard = boardRepository.save(board);

    return toEnrichedDto(savedBoard, userId);
  }

  private void requireEditable(SessionEntity session) {
    if (session.isSubscribed()) {
      throw new ForbiddenException("Boards of a subscribed session cannot be added or removed");
    }
  }

  private void applyPlaybackUpdate(BoardEntity board, BoardUpdateRequest request) {
    if (changesBoardSetup(board, request)) {
      throw new ForbiddenException("Boards of a subscribed session only allow playback changes");
    }

    if (request.getVolume() != null) {
      board.setVolume(request.getVolume());
    }
    boolean pauseEnabled = board.getRepeatGapMaxSec() > 0;
    applyRepeatGap(board, request);
    if (board.getRepeatGapMaxSec() > 0 != pauseEnabled) {
      throw new ForbiddenException("The pause between plays of a shared stage can only be adjusted, not turned on or off");
    }

    if (request.getSelectedTrackId() == null) {
      board.setSelectedTrack(null);
    } else {
      TrackEntity track = boardEnricher.getAvailableTrackEntities(board).stream()
              .filter(available -> available.getId().equals(request.getSelectedTrackId()))
              .findFirst()
              .orElseThrow(() -> new NotFoundException(
                      String.format("Track with id %s not found", request.getSelectedTrackId())
              ));
      board.setSelectedTrack(track);
    }

    if (request.getSelectedWindowId() == null) {
      board.setSelectedWindow(null);
    } else {
      TrackWindowEntity window = board.getSelectedTrack() == null ? null : board.getSelectedTrack().getTrackWindows().stream()
              .filter(candidate -> candidate.getId().equals(request.getSelectedWindowId()))
              .findFirst()
              .orElse(null);
      if (window == null) {
        throw new NotFoundException(String.format("Window with id %s not found", request.getSelectedWindowId()));
      }
      board.setSelectedWindow(window);
    }

    board.getSession().setModified(true);
  }

  private void applyRepeatGap(BoardEntity board, BoardUpdateRequest request) {
    int min = request.getRepeatGapMinSec() == null ? board.getRepeatGapMinSec() : request.getRepeatGapMinSec();
    int max = request.getRepeatGapMaxSec() == null ? board.getRepeatGapMaxSec() : request.getRepeatGapMaxSec();
    if (min > max) {
      throw new BadRequestException("Repeat gap minimum must not exceed its maximum");
    }
    board.setRepeatGapMinSec(min);
    board.setRepeatGapMaxSec(max);
  }

  private boolean changesBoardSetup(BoardEntity board, BoardUpdateRequest request) {
    UUID currentGroupId = board.getSelectedGroup() == null ? null : board.getSelectedGroup().getId();
    LinkedBoard current = board.getLinkedBoard();
    UUID currentLinkedId = current == null ? null : current.getBoardId();
    LinkedBoardMode currentLinkedMode = currentLinkedId == null ? null : current.getMode();
    LinkedBoard requested = boardMapper.toLinkedBoard(request.getLinkedBoard());
    UUID requestedLinkedId = requested == null ? null : requested.getBoardId();
    LinkedBoardMode requestedLinkedMode = requestedLinkedId == null ? null : requested.getMode();

    return (request.getName() != null && !request.getName().equals(board.getName()))
            || !Objects.equals(request.getSelectedGroupId(), currentGroupId)
            || !Objects.equals(requestedLinkedId, currentLinkedId)
            || !Objects.equals(requestedLinkedMode, currentLinkedMode);
  }

  private void setTrackIfExist(UUID selectedTrackId, BoardEntity board) {
    if (selectedTrackId == null) {
      board.setSelectedTrack(null);
      return;
    }

    UUID currentUserId = securityUtils.getCurrentUserId();

    TrackEntity track = trackRepository.findAccessibleByIdAndUserId(selectedTrackId, currentUserId)
            .orElseThrow(() -> new NotFoundException(
                    String.format("Track with id %s not found", selectedTrackId)
            ));

    if ((board.getSelectedTrack() == null || !board.getSelectedTrack().getId().equals(track.getId()))
            && !isInSessionGroup(board.getSession(), track)) {
      board.getSession().getTracks().add(track);
    }
    board.setSelectedTrack(track);
  }

  private void setGroupIfExist(UUID selectedGroupId, BoardEntity board) {
    if (selectedGroupId == null) {
      board.setSelectedGroup(null);
      return;
    }
    GroupEntity group = groupRepository.findByIdAndOwner_IdAndManagedSessionIsNull(selectedGroupId, securityUtils.getCurrentUserId())
            .orElseThrow(() -> new NotFoundException(String.format("Group with id %s not found", selectedGroupId)));
    if (board.getSelectedGroup() == null || !board.getSelectedGroup().getId().equals(group.getId())) {
      board.getSession().getGroups().add(group);
    }
    board.setSelectedGroup(group);
  }

  private boolean isInSessionGroup(SessionEntity session, TrackEntity track) {
    return session.getGroups().stream()
            .flatMap(group -> group.getGroupTracks().stream())
            .anyMatch(groupTrack -> groupTrack.getTrackWindow() == null
                    && groupTrack.getTrack().getId().equals(track.getId()));
  }

  private void setWindowIfExist(UUID selectedWindowId, BoardEntity board) {
    if (selectedWindowId == null) {
      board.setSelectedWindow(null);
      return;
    }
    TrackWindowEntity window = trackWindowRepository.findById(selectedWindowId)
            .orElseThrow(() -> new NotFoundException(String.format("Window with id %s not found", selectedWindowId)));
    if (board.getSelectedTrack() != null && !board.getSelectedTrack().getTrackWindows().contains(window)) {
      throw new NotFoundException(String.format("Window with id %s not found", selectedWindowId));
    }

    board.setSelectedWindow(window);
  }

  private Board toEnrichedDto(BoardEntity boardEntity, UUID userId) {
    Board boardDto = boardMapper.toDto(boardEntity);
    return boardEnricher.enrich(boardDto, boardEntity, userId);
  }
}
