package board;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** 4-2 XSS 와 CSP: th:text 자동 이스케이프 + CSP·nosniff 헤더 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PostXssTest {
    static final String PAYLOAD = "<img src=x onerror=alert(document.cookie)>";

    @Autowired MockMvc mvc;
    @Autowired PostRepository posts;

    @Test
    @DisplayName("태그가 든 본문은 이스케이프되어 출력된다")
    void contentIsEscaped() throws Exception {
        long id = posts.save(new Post("이벤트 안내", PAYLOAD, "bob")).getId();
        mvc.perform(get("/api/posts/{id}/view", id))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("text/html"))
            .andExpect(content().string(containsString(
                "<div class=\"content\">&lt;img src=x onerror=alert(document.cookie)&gt;</div>")))
            .andExpect(content().string(not(containsString(PAYLOAD))));
    }

    @Test
    @DisplayName("보기 화면에 CSP와 nosniff 헤더가 있다")
    void cspAndNosniff() throws Exception {
        long id = posts.save(new Post("공지", "본문", "admin")).getId();
        mvc.perform(get("/api/posts/{id}/view", id))
            .andExpect(header().string("Content-Security-Policy", containsString("script-src 'self'")))
            .andExpect(header().string("Content-Security-Policy", containsString("object-src 'none'")))
            .andExpect(header().string("Content-Security-Policy", containsString("frame-ancestors 'none'")))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }
}
