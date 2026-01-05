package org.dnd.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.dnd.api.model.BoardCreateRequest;
import org.dnd.api.model.BoardUpdateRequest;
import org.dnd.api.model.UserAuthDTO;
import org.dnd.model.BoardEntity;
import org.dnd.model.UserEntity;
import org.dnd.repository.BoardRepository;
import org.dnd.repository.DatabaseBase;
import org.dnd.repository.UserRepository;
import org.dnd.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BoardControllerTest extends DatabaseBase {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private BoardRepository boardRepository;
    @Autowired
    private JwtService jwtService;

    private UserEntity testUser;
    private String authToken;

    @BeforeEach
    void setUp() {
        boardRepository.deleteAll();
        userRepository.deleteAll();

        testUser = new UserEntity();
        testUser.setName("testUser");
        testUser.setPassword("password");
        testUser = userRepository.save(testUser);

        UserAuthDTO userAuth = new UserAuthDTO();
        userAuth.setId(testUser.getId());
        userAuth.setName(testUser.getName());
        authToken = jwtService.generateToken(userAuth);
    }

    @Test
    void getUserBoards_Success() throws Exception {
        BoardEntity board = new BoardEntity();
        board.setOwner(testUser);
        board.setName("Test name");
        board.setVolume(50);
        board.setRepeat(false);
        board.setOverplay(false);
        boardRepository.save(board);

        mockMvc.perform(get("/api/v1/boards")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].volume").value(50))
                .andExpect(jsonPath("$[0].name").value("Test name"))
                .andExpect(jsonPath("$[0].repeat").value(false))
                .andExpect(jsonPath("$[0].overplay").value(false));
    }

    @Test
    void createUserBoard_Success() throws Exception {
        BoardCreateRequest request = new BoardCreateRequest()
                .volume(75)
                .repeat(true)
                .overplay(false)
                .name("Test Board");

        mockMvc.perform(post("/api/v1/boards")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Test Board"))
                .andExpect(jsonPath("$.volume").value(75))
                .andExpect(jsonPath("$.repeat").value(true))
                .andExpect(jsonPath("$.overplay").value(false));

        assertFalse(boardRepository.findByOwner_Id(testUser.getId()).isEmpty());
    }

    @Test
    void updateUserBoard_Success() throws Exception {
        BoardEntity board = new BoardEntity();
        board.setName("Original Board");
        board.setOwner(testUser);
        board.setVolume(50);
        board.setRepeat(false);
        board.setOverplay(false);
        board = boardRepository.save(board);

        BoardUpdateRequest updateRequest = new BoardUpdateRequest()
                .volume(100)
                .repeat(true)
                .overplay(true);

        mockMvc.perform(put("/api/v1/boards/{boardId}", board.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Original Board"))
                .andExpect(jsonPath("$.volume").value(100))
                .andExpect(jsonPath("$.repeat").value(true))
                .andExpect(jsonPath("$.overplay").value(true));
    }

    @Test
    void deleteUserBoard_Success() throws Exception {
        BoardEntity board = new BoardEntity();
        board.setName("Board to Delete");
        board.setOwner(testUser);
        board.setVolume(50);
        board = boardRepository.save(board);

        mockMvc.perform(delete("/api/v1/boards/{boardId}", board.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken))
                .andExpect(status().isNoContent());

        assertFalse(boardRepository.existsById(board.getId()));
    }

    @Test
    void getUserBoards_EmptyList() throws Exception {
        mockMvc.perform(get("/api/v1/boards")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken))
                .andExpect(status().isNoContent());
    }

    @Test
    void getUserBoards_NoAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/boards"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getUserBoards_InvalidToken() throws Exception {
        mockMvc.perform(get("/api/v1/boards")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid.token.here"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getUserBoards_WrongUser() throws Exception {
        UserEntity otherUser = new UserEntity();
        otherUser.setName("otherUser");
        otherUser.setPassword("password");
        otherUser = userRepository.save(otherUser);

        BoardEntity board = new BoardEntity();
        board.setName("Other User's Board");
        board.setOwner(otherUser);
        board.setVolume(50);
        boardRepository.save(board);


        mockMvc.perform(get("/api/v1/boards")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + "wrongUserToken"))
                .andExpect(status().isForbidden());
    }


    @Test
    void updateUserBoard_NotFound() throws Exception {
        BoardUpdateRequest updateRequest = new BoardUpdateRequest()
                .volume(100)
                .repeat(true);

        mockMvc.perform(put("/api/v1/boards/{boardId}", 999L)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isNotFound());
    }

}