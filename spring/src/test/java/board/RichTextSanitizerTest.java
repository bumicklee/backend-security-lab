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

/** 4-2 서식 있는 본문: OWASP Java HTML Sanitizer (FORMATTING + LINKS + BLOCKS) 허용 목록 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RichTextSanitizerTest {
    @Autowired MockMvc mvc;
    @Autowired PostRepository posts;

    @Test
    @DisplayName("허용 목록 밖 태그·속성은 정화된다")
    void sanitized() throws Exception {
        String dirty = "<p onclick=\"steal()\"><b>굵게</b><script>alert(1)</script>"
            + "<a href=\"javascript:alert(1)\">링크</a></p><img src=x onerror=alert(1)>";
        long id = posts.save(new Post("서식", dirty, "bob")).getId();
        mvc.perform(get("/api/posts/{id}/rich", id))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("<b>굵게</b>")))
            .andExpect(content().string(not(containsString("<script>alert"))))
            .andExpect(content().string(not(containsString("onclick"))))
            .andExpect(content().string(not(containsString("onerror"))))
            .andExpect(content().string(not(containsString("javascript:"))));
    }
}
