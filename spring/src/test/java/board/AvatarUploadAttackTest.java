package board;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** 2-5 파일 업로드 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AvatarUploadAttackTest {
    @Autowired MockMvc mvc;

    // PNG 시그니처 + IHDR 일부 (실습용 최소 바이트)
    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n',
        0, 0, 0, 13, 'I', 'H', 'D', 'R', 0, 0, 0, 1, 0, 0, 0, 1, 8, 2, 0, 0, 0};

    @Test
    @DisplayName("GIF 헤더를 붙인 JSP는 400")
    void disguisedJspIsRejected() throws Exception {
        byte[] jsp = "GIF89a<%@ page import=\"java.io.*\" %><% /* webshell 자리 */ %>".getBytes(StandardCharsets.UTF_8);
        var file = new MockMultipartFile("file", "avatar.jpg.jsp", "image/jpeg", jsp);
        mvc.perform(multipart("/api/avatars").file(file).with(user("alice")).with(csrf()))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("SVG·HTML 은 이미지 헤더를 달아도 400")
    void svgAndHtmlAreRejected() throws Exception {
        var svg = new MockMultipartFile("file", "a.svg", "image/svg+xml",
            "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>".getBytes());
        var html = new MockMultipartFile("file", "a.png", "image/png", "<html><script>alert(1)</script>".getBytes());
        mvc.perform(multipart("/api/avatars").file(svg).with(user("alice")).with(csrf()))
            .andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/avatars").file(html).with(user("alice")).with(csrf()))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("저장 이름에 원본 이름이 쓰이지 않는다")
    void storedNameIsGenerated() throws Exception {
        var file = new MockMultipartFile("file", "../../cat.png", "application/octet-stream", PNG);
        String body = mvc.perform(multipart("/api/avatars").file(file).with(user("alice")).with(csrf()))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.id");
        assertThat(id).matches("^[0-9a-f-]{36}\\.png$").doesNotContain("cat");

        mvc.perform(get("/api/avatars/" + id))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("attachment")))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    @DisplayName("업로드는 로그인과 CSRF 토큰이 필요하다")
    void uploadNeedsLoginAndCsrf() throws Exception {
        var file = new MockMultipartFile("file", "cat.png", "image/png", PNG);
        mvc.perform(multipart("/api/avatars").file(file).with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(multipart("/api/avatars").file(file).with(user("alice"))).andExpect(status().isForbidden());
    }
}
