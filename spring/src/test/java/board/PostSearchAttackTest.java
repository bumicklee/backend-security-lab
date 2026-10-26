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

/** 2-3 SQL 인젝션: 바인딩한 /api/posts 는 공격 입력을 그냥 "제목 문자열"로 취급한다 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PostSearchAttackTest {
    @Autowired MockMvc mvc;

    @Test
    @DisplayName("정상 검색은 해당 글만 돌려준다")
    void normalSearch() throws Exception {
        mvc.perform(get("/api/posts").param("title", "공지"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].author").value("admin"));
    }

    @Test
    @DisplayName("항상 참인 조건은 0건을 돌려준다")
    void alwaysTrueReturnsNothing() throws Exception {
        mvc.perform(get("/api/posts").param("title", "' OR 1=1--"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("UNION 입력은 회원 정보를 꺼내지 못한다")
    void unionCannotReadUsers() throws Exception {
        mvc.perform(get("/api/posts").param("title", "' UNION SELECT id, email, password_hash FROM users--"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("@example.com"))));
    }

    @Test
    @DisplayName("ORDER BY 는 허용 목록에 없는 컬럼이면 400")
    void sortAllowList() throws Exception {
        mvc.perform(get("/api/posts/search").param("sort", "title"))
            .andExpect(status().isOk());
        mvc.perform(get("/api/posts/search").param("sort", "password_hash"))
            .andExpect(status().isBadRequest());
        mvc.perform(get("/api/posts/search").param("sort", "(CASE WHEN 1=1 THEN id END)"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("LIKE 검색의 % 는 와일드카드가 아니라 글자로 취급")
    void likeIsEscaped() throws Exception {
        mvc.perform(get("/api/posts/search").param("keyword", "%"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));
    }
}
