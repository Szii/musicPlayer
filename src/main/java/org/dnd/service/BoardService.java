package org.dnd.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dnd.api.model.Board;
import org.dnd.api.model.BoardCreateRequest;
import org.dnd.api.model.BoardUpdateRequest;
import org.dnd.api.model.Track;
import org.dnd.exception.NotFoundException;
import org.dnd.mappers.BoardMapper;
import org.dnd.model.BoardEntity;
import org.dnd.model.TrackEntity;
import org.dnd.model.UserEntity;
import org.dnd.repository.BoardRepository;
import org.dnd.repository.TrackRepository;
import org.dnd.repository.UserRepository;
import org.dnd.utils.SecurityUtils;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class BoardService {
    private final BoardRepository boardRepository;
    private final UserRepository userRepository;
    private final TrackRepository trackRepository;
    private final BoardMapper boardMapper;

    public List<Board> getUserBoards() {
        log.debug("Getting boards for user with id {}", SecurityUtils.getCurrentUserId());
        return boardMapper.toDtos(boardRepository.findByOwner_Id(SecurityUtils.getCurrentUserId()));
    }

    public Board createUserBoard(BoardCreateRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        log.debug("Creating board for user with id {}", userId);
        UserEntity owner = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException(String.format("User with id %d not found", userId)));

        BoardEntity board = boardMapper.toEntity(request);
        board.setOwner(owner);

        setTrackIfExist(request.getSelectedTrack(), board);

        return boardMapper.toDto(boardRepository.save(board));
    }

    public void deleteUserBoard(Long boardId) {
        Long userId = SecurityUtils.getCurrentUserId();
        System.out.println(boardId);
        System.out.println(userId);
        log.debug("Deleting board {} for user {}", boardId, userId);
        if (!boardRepository.existsByIdAndOwner_Id(boardId, userId)) {
            throw new NotFoundException(String.format("Board with id %d not found for user %d", boardId, userId));
        }
        boardRepository.deleteById(boardId);
    }

    public Board updateUserBoard(Long boardId, BoardUpdateRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        log.debug("Updating board {} for user {}", boardId, userId);
        BoardEntity board = boardRepository.findByIdAndOwner_Id(boardId, userId)
                .orElseThrow(() -> new NotFoundException(String.format("Board with id %d not found for user %d", boardId, userId)));

        setTrackIfExist(request.getSelectedTrack(), board);

        boardMapper.updateBoardFromRequest(request, board);
        return boardMapper.toDto(boardRepository.save(board));
    }

    public void setTrackIfExist(Track selectedTrack, BoardEntity board) {
        if (selectedTrack != null && selectedTrack.getId() != null) {
            TrackEntity track = trackRepository.findById(selectedTrack.getId())
                    .orElseThrow(() -> new NotFoundException(String.format("Track with id %d not found", selectedTrack.getId())));
            board.setSelectedTrack(track);
        }
    }
}
