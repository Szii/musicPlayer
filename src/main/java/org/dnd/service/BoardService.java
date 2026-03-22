package org.dnd.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dnd.api.model.Board;
import org.dnd.api.model.BoardCreateRequest;
import org.dnd.api.model.BoardUpdateRequest;
import org.dnd.api.model.Track;
import org.dnd.exception.ForbiddenException;
import org.dnd.exception.NotFoundException;
import org.dnd.mappers.BoardMapper;
import org.dnd.mappers.TrackMapper;
import org.dnd.model.BoardEntity;
import org.dnd.model.GroupEntity;
import org.dnd.model.TrackEntity;
import org.dnd.model.UserEntity;
import org.dnd.repository.BoardRepository;
import org.dnd.repository.GroupRepository;
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
    private final GroupRepository groupRepository;
    private final BoardMapper boardMapper;
    private final TrackMapper trackMapper;

    public List<Board> getUserBoards() {
        log.debug("Getting boards for user with id {}", SecurityUtils.getCurrentUserId());
        return boardMapper.toDtos(boardRepository.findByOwner_Id(SecurityUtils.getCurrentUserId()));
    }

    public Board getUserBoard(Long boardId) {
        log.debug("Getting single board for user with id {}", SecurityUtils.getCurrentUserId());
        BoardEntity board = boardRepository.findById(boardId)
                .orElseThrow(() -> new NotFoundException(String.format("Board with id %d not found", boardId)));
        if (!board.getOwner().getId().equals(SecurityUtils.getCurrentUserId())) {
            throw new ForbiddenException("You can get only a board which you own");
        }
        return boardMapper.toDto(board);
    }

    public Board createUserBoard(BoardCreateRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        log.debug("Creating board for user with id {}", userId);
        UserEntity owner = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException(String.format("User with id %d not found", userId)));
        BoardEntity board = boardMapper.toEntity(request);
        board.setOwner(owner);

        setTrackIfExist(request.getSelectedTrackId(), board);
        setGroupIfExist(request.getSelectedGroupId(), board);

        Board boardDto = boardMapper.toDto(boardRepository.save(board));
        boardDto.setAvailableTracks(getTracksForBoard(board.getId()));
        return boardDto;
    }

    public void deleteUserBoard(Long boardId) {
        Long userId = SecurityUtils.getCurrentUserId();
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

        boardMapper.updateBoardFromRequest(request, board);
        setTrackIfExist(request.getSelectedTrackId(), board);
        setGroupIfExist(request.getSelectedGroupId(), board);

        Board boardDto = boardMapper.toDto(boardRepository.save(board));
        boardDto.setAvailableTracks(getTracksForBoard(boardId));
        System.out.println("Available tracks " + boardDto.getAvailableTracks().size());
        return boardDto;
    }

    public void setTrackIfExist(Long selectedTrackId, BoardEntity board) {
        if (selectedTrackId == null) {
            board.setSelectedTrack(null);
            return;
        }

        TrackEntity track = trackRepository.findById(selectedTrackId)
                .orElseThrow(() -> new NotFoundException(String.format("Track with id %d not found", selectedTrackId)));
        board.setSelectedTrack(track);
    }

    public void setGroupIfExist(Long selectedGroupId, BoardEntity board) {
        if (selectedGroupId == null) {
            board.setSelectedGroup(null);
            return;
        }
        GroupEntity group = groupRepository.findById(selectedGroupId)
                .orElseThrow(() -> new NotFoundException(String.format("Group with id %d not found", selectedGroupId)));
        board.setSelectedGroup(group);

    }

    private List<Track> getTracksForBoard(Long boardId) {
        Long userId = SecurityUtils.getCurrentUserId();
        log.debug("Getting tracks for board {} and user {}", boardId, userId);
        BoardEntity board = boardRepository.findByIdAndOwner_Id(boardId, userId)
                .orElseThrow(() -> new NotFoundException(String.format("Board with id %d not found for user %d", boardId, userId)));
        List<TrackEntity> tracks;
        if (board.getSelectedGroup() != null) {
            tracks = trackRepository.findByGroups_Id(board.getSelectedGroup().getId());
        } else {
            tracks = trackRepository.findByOwner_Id(userId);
        }


        List<Track> result = tracks.stream()
                .map(trackMapper::toDto)
                .toList();

        System.out.println("Mapped tracks: " + result.size());
        return result;
    }
}
