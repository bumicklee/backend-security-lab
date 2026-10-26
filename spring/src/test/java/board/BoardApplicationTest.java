package board;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** 기본 프로필로 컨텍스트가 뜨고, 취약한 /lab/** 은 닫혀 있는지 확인 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BoardApplicationTest {
    @Autowired MockMvc mvc;

    @Test
    @DisplayName("상태 확인 주소 /health 는 UP")
    void health() throws Exception {
        mvc.perform(get("/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"))
            // 화면과 같은 {"status":"UP"} 만 (probes 그룹 이름 등 내부 정보 노출 없음)
            .andExpect(jsonPath("$.groups").doesNotExist());
    }

    @Test
    @DisplayName("기본 프로필에서는 /lab/** 이 열리지 않는다")
    void labIsClosedByDefault() throws Exception {
        mvc.perform(get("/lab/posts").param("title", "공지"))
            .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("Spring Security 기본 보안 헤더가 붙는다")
    void securityHeaders() throws Exception {
        mvc.perform(get("/api/posts"))
            .andExpect(status().isOk())
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andExpect(header().string("X-Frame-Options", "DENY"));
    }
}
