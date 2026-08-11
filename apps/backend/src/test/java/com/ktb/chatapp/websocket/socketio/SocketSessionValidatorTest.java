package com.ktb.chatapp.websocket.socketio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ktb.chatapp.service.SessionService;
import com.ktb.chatapp.service.SessionValidationResult;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SocketSessionValidatorTest {

    @Mock private SessionService sessionService;
    private SocketSessionValidator validator;

    @BeforeEach
    void setUp() {
        validator = new SocketSessionValidator(sessionService);
        ReflectionTestUtils.setField(validator, "validationCacheTtl", "5s");
        ReflectionTestUtils.setField(validator, "activityUpdateInterval", "30s");
    }

    @Test
    void repeatedMessagesReuseValidationForFiveSeconds() {
        SessionValidationResult valid = SessionValidationResult.valid(null);
        when(sessionService.validateSession("user-1", "session-1", Duration.ofSeconds(30)))
                .thenReturn(valid);

        assertThat(validator.validate("user-1", "session-1").isValid()).isTrue();
        assertThat(validator.validate("user-1", "session-1").isValid()).isTrue();

        verify(sessionService, times(1))
                .validateSession("user-1", "session-1", Duration.ofSeconds(30));
    }

    @Test
    void invalidationForcesImmediateSessionRecheck() {
        when(sessionService.validateSession("user-1", "session-1", Duration.ofSeconds(30)))
                .thenReturn(SessionValidationResult.valid(null));

        validator.validate("user-1", "session-1");
        validator.invalidate("user-1");
        validator.validate("user-1", "session-1");

        verify(sessionService, times(2))
                .validateSession("user-1", "session-1", Duration.ofSeconds(30));
    }
}
