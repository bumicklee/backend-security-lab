package board;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** 2-4 커맨드 인젝션과 경로 조작 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CommandPathAttackTest {
    @Autowired MockMvc mvc;

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"203.0.113.10; id", "203.0.113.10; sleep 5", "$(id)", "`id`", "a|id"})
    @DisplayName("세미콜론이 섞인 host는 400")
    void injectedHostIsRejected(String host) throws Exception {
        long start = System.nanoTime();
        mvc.perform(get("/api/admin/ping").param("host", host).with(user("admin").roles("ADMIN")))
            .andExpect(status().isBadRequest());
        // sleep 5 가 실행됐다면 1초를 넘긴다
        org.assertj.core.api.Assertions.assertThat(System.nanoTime() - start).isLessThan(1_000_000_000L);
    }

    @Test
    @DisplayName("ping 은 관리자만 (비로그인 401, 일반 사용자 403)")
    void pingIsAdminOnly() throws Exception {
        mvc.perform(get("/api/admin/ping").param("host", "localhost")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/ping").param("host", "localhost").with(user("alice")))
            .andExpect(status().isForbidden());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"../../etc/passwd", "../../../app/.env", "/etc/passwd", "sub/../../secret.txt"})
    @DisplayName("../ 경로는 400")
    void traversalIsRejected(String name) throws Exception {
        mvc.perform(get("/api/files").param("name", name))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("기준 폴더 안의 파일은 attachment + nosniff 로 내려간다")
    void normalFile() throws Exception {
        mvc.perform(get("/api/files").param("name", "hello.txt"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("attachment")))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }
}
