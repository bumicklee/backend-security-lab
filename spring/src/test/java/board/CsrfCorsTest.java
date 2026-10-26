package board;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 4-5 CORS 와 CSRF: csrf.spa()(XSRF-TOKEN 쿠키 → X-XSRF-TOKEN 헤더) + 허용 출처만 CORS.
 * MockMvc 의 csrf() 는 컨텍스트의 CSRF 저장소를 바꿔 버리므로 쓰지 않고, 별도 컨텍스트(properties)에서 돈다.
 */
@SpringBootTest(properties = "app.cors.allowed-origins=https://app.example.com")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CsrfCorsTest {
    static final String ALICE = "{\"username\":\"alice\",\"password\":\"alice-EXAMPLE-pw\"}";
    static final String POST_JSON = "{\"title\":\"제목\",\"content\":\"본문\"}";

    @Autowired MockMvc mvc;

    /** 브라우저(SPA)처럼: XSRF-TOKEN 쿠키를 받아 X-XSRF-TOKEN 헤더로 돌려보내 로그인 → 세션 쿠키 */
    MockHttpSession loginAsAlice() throws Exception {
        Cookie xsrf = xsrfCookie(null);
        var result = mvc.perform(post("/api/auth/login").cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue())
                .contentType(MediaType.APPLICATION_JSON).content(ALICE))
            .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    Cookie xsrfCookie(MockHttpSession session) throws Exception {
        var req = get("/api/csrf");
        if (session != null) req.session(session);
        return mvc.perform(req).andExpect(cookie().exists("XSRF-TOKEN")).andReturn()
            .getResponse().getCookie("XSRF-TOKEN");
    }

    @Test
    @DisplayName("CSRF 토큰 없는 쿠키 요청은 403")
    void cookieRequestWithoutTokenIsForbidden() throws Exception {
        MockHttpSession session = loginAsAlice();
        mvc.perform(post("/api/posts").session(session)
                .contentType(MediaType.APPLICATION_JSON).content(POST_JSON))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("XSRF-TOKEN 쿠키 값을 X-XSRF-TOKEN 헤더로 보내면 통과한다")
    void cookieToHeaderPasses() throws Exception {
        MockHttpSession session = loginAsAlice();
        Cookie xsrf = xsrfCookie(session);
        mvc.perform(post("/api/posts").session(session).cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue())
                .contentType(MediaType.APPLICATION_JSON).content(POST_JSON))
            .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("허용하지 않은 Origin에는 CORS 헤더를 주지 않는다")
    void unknownOriginGetsNoCorsHeaders() throws Exception {
        mvc.perform(get("/api/posts").header("Origin", "https://attacker.example.net"))
            .andExpect(header().doesNotExist("Access-Control-Allow-Origin"))
            .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
    }

    @Test
    @DisplayName("허용한 Origin 에는 그 출처와 자격 증명 허용을 돌려준다")
    void allowedOriginPreflight() throws Exception {
        mvc.perform(options("/api/posts").header("Origin", "https://app.example.com")
                .header("Access-Control-Request-Method", "POST"))
            .andExpect(status().isOk())
            .andExpect(header().string("Access-Control-Allow-Origin", "https://app.example.com"))
            .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }
}
