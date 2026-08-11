package com.ktb.chatapp.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ktb.chatapp.repository.UserRepository;
import com.ktb.chatapp.security.SessionAwareJwtAuthenticationConverter;
import com.ktb.chatapp.service.RateLimitService;
import com.ktb.chatapp.service.RecentMessageCounter;
import com.ktb.chatapp.service.RoomService;
import java.security.Principal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = RoomController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("RoomController 채팅방 참여 표면 계약")
class RoomControllerJoinTest {

    private static final String EMAIL = "user@example.com";
    private static final String ROOM_ID = "60d5ec49f1b2c8b9e8c4f2a1";
    private static final String BODY = "{\"password\":\"wrong-password\"}";
    private static final Principal PRINCIPAL = () -> EMAIL;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RoomService roomService;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private RecentMessageCounter recentMessageCounter;

    @MockitoBean
    private RateLimitService rateLimitService;

    @MockitoBean
    private SessionAwareJwtAuthenticationConverter jwtAuthenticationConverter;

    @Test
    @DisplayName("비밀번호 불일치는 401이 아니라 403으로 응답한다")
    void joinRoom_passwordMismatch_returnsForbidden() throws Exception {
        when(roomService.joinRoom(any(), any(), any()))
                .thenThrow(new RuntimeException("비밀번호가 일치하지 않습니다."));

        mockMvc.perform(post("/api/rooms/{roomId}/join", ROOM_ID)
                        .principal(PRINCIPAL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("비밀번호가 일치하지 않습니다."));
    }

    @Test
    @DisplayName("존재하지 않는 방은 404로 응답한다")
    void joinRoom_missingRoom_returnsNotFound() throws Exception {
        when(roomService.joinRoom(any(), any(), any())).thenReturn(null);

        mockMvc.perform(post("/api/rooms/{roomId}/join", ROOM_ID)
                        .principal(PRINCIPAL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("채팅방을 찾을 수 없습니다."));
    }

    @Test
    @DisplayName("비밀번호와 무관한 실패는 400으로 응답해 403과 구분된다")
    void joinRoom_otherFailure_returnsBadRequest() throws Exception {
        when(roomService.joinRoom(any(), any(), any()))
                .thenThrow(new RuntimeException("사용자를 찾을 수 없습니다: " + EMAIL));

        mockMvc.perform(post("/api/rooms/{roomId}/join", ROOM_ID)
                        .principal(PRINCIPAL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }
}
