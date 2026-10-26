package board;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import board.user.AppUser;
import board.user.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.password.CompromisedPasswordChecker;
import org.springframework.security.authentication.password.CompromisedPasswordDecision;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 3-5 무차별 대입·크리덴셜 스터핑. HaveIBeenPwned 는 @MockitoBean 으로 바꿔 끼워 네트워크 없이 확인한다.
 * 테스트마다 새 계정을 만들어, 실패 횟수가 다른 테스트로 새지 않게 한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LoginProtectionTest {
    @Autowired MockMvc mvc;
    @Autowired AppUserRepository users;
    @Autowired PasswordEncoder encoder;
    @MockitoBean CompromisedPasswordChecker compromisedPasswordChecker;

    @BeforeEach
    void notCompromisedByDefault() {
        given(compromisedPasswordChecker.check(anyString())).willReturn(new CompromisedPasswordDecision(false));
    }

    private String newUser(String name, String password) {
        users.save(new AppUser(name, name + "@example.com", encoder.encode(password), "USER"));
        return name;
    }

    private ResultActions login(String username, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"));
    }

    @Test
    @DisplayName("유출된 비밀번호로 로그인하면 재설정을 요구한다")
    void compromisedPasswordRequiresReset() throws Exception {
        String u = newUser("pwned-user", "P@ssw0rd-leaked");
        given(compromisedPasswordChecker.check("P@ssw0rd-leaked")).willReturn(new CompromisedPasswordDecision(true));

        login(u, "P@ssw0rd-leaked")
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error").value("password_reset_required"));
    }

    @Test
    @DisplayName("다섯 번 실패하면 맞는 비밀번호도 429")
    void fiveFailuresThen429() throws Exception {
        String u = newUser("brute-user", "brute-EXAMPLE-pw");
        for (int i = 0; i < 5; i++) {
            login(u, "wrong").andExpect(status().isUnauthorized());
        }
        login(u, "brute-EXAMPLE-pw")
            .andExpect(status().isTooManyRequests())
            .andExpect(header().string("Retry-After", "60"));
        // 대소문자만 바꿔도 같은 계정으로 센다
        login("BRUTE-USER", "brute-EXAMPLE-pw").andExpect(status().isTooManyRequests());
    }
}
