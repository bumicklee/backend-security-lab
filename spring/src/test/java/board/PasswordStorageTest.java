package board;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import board.user.AppUser;
import board.user.AppUserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** 3-1 비밀번호 저장: 새 해시는 {argon2}, 예전 {bcrypt} 해시는 로그인 순간에 {argon2} 로 이전 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PasswordStorageTest {
    @Autowired MockMvc mvc;
    @Autowired PasswordEncoder encoder;
    @Autowired AppUserRepository users;

    @Test
    @DisplayName("새 비밀번호는 {argon2}로 저장된다")
    void newPasswordsUseArgon2() {
        String a = encoder.encode("same-password-EXAMPLE");
        String b = encoder.encode("same-password-EXAMPLE");
        // 알고리즘 접두어 + Argon2id + OWASP 최소 파라미터가 해시 안에 적힌다
        assertThat(a).startsWith("{argon2}$argon2id$v=19$m=19456,t=2,p=1$");
        assertThat(a).isNotEqualTo(b);                       // 계정마다 다른 솔트
        assertThat(encoder.matches("same-password-EXAMPLE", a)).isTrue();
    }

    @Test
    @DisplayName("{bcrypt} 사용자는 로그인 후 {argon2}로 바뀐다")
    void bcryptHashIsUpgradedOnLogin() throws Exception {
        String legacy = "{bcrypt}" + new BCryptPasswordEncoder().encode("legacy-EXAMPLE-pw");
        users.save(new AppUser("legacy", "legacy@example.com", legacy, "USER"));

        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"legacy\",\"password\":\"legacy-EXAMPLE-pw\"}"))
            .andExpect(status().isOk());

        String after = users.findByUsername("legacy").orElseThrow().getPasswordHash();
        assertThat(after).startsWith("{argon2}$argon2id$");
        assertThat(encoder.matches("legacy-EXAMPLE-pw", after)).isTrue();
    }
}
