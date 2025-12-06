package org.dnd.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dnd.api.model.Board;
import org.dnd.api.model.BoardCreateRequest;
import org.dnd.api.model.BoardUpdateRequest;
import org.dnd.exception.NotFoundException;
import org.dnd.mappers.BoardMapper;
import org.dnd.model.BoardEntity;
import org.dnd.model.TrackEntity;
import org.dnd.model.UserEntity;
import org.dnd.repository.BoardRepository;
import org.dnd.repository.TrackRepository;
import org.dnd.repository.UserRepository;
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

    public List<Board> getUserBoards(Long userId) {
        log.debug("Getting boards for user with id {}", userId);
        return boardMapper.toDtos(boardRepository.findByOwner_Id(userId));
    }

    public Board createUserBoard(Long userId, BoardCreateRequest request) {
        log.debug("Creating board for user with id {}", userId);
        UserEntity owner = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException(String.format("User with id %d not found", userId)));

        BoardEntity board = boardMapper.toEntity(request);
        board.setOwner(owner);

        if (request.getSelectedTrack().getId() != null) {
            TrackEntity track = trackRepository.findById(request.getSelectedTrack().getId())
                    .orElseThrow(() -> new NotFoundException(String.format("Track with id %d not found", request.getSelectedTrack().getId())));
            board.setSelectedTrack(track);
        }

        return boardMapper.toDto(boardRepository.save(board));
    }

    public void deleteUserBoard(Long userId, Long boardId) {
        log.debug("Deleting board {} for user {}", boardId, userId);
        if (!boardRepository.existsByIdAndOwner_Id(boardId, userId)) {
            throw new NotFoundException(String.format("Board with id %d not found for user %d", boardId, userId));
        }
        boardRepository.deleteById(boardId);
    }

    public Board updateUserBoard(Long userId, Long boardId, BoardUpdateRequest request) {
        log.debug("Updating board {} for user {}", boardId, userId);
        BoardEntity board = boardRepository.findByIdAndOwner_Id(boardId, userId)
                .orElseThrow(() -> new NotFoundException(String.format("Board with id %d not found for user %d", boardId, userId)));

        if (request.getSelectedTrack().getId() != null) {
            TrackEntity track = trackRepository.findById(request.getSelectedTrack().getId())
                    .orElseThrow(() -> new NotFoundException(String.format("Track with id %d not found", request.getSelectedTrack().getId())));
            board.setSelectedTrack(track);
        }

        boardMapper.updateBoardFromRequest(request, board);
        return boardMapper.toDto(boardRepository.save(board));
    }
}
