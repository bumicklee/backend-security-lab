package board;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 세션 로그인 · CSRF · 세션 고정 방어 · 쿠키 속성 · 로그아웃 (application.yml 의 가짜 시드 비밀번호 사용).
 * 쿠키 속성(server.servlet.session.cookie.*)은 실제 서블릿 컨테이너가 붙이므로 RANDOM_PORT 로 띄운다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthLoginTest {
    @Autowired MockMvc mvc;
    @LocalServerPort int port;

    static final String ALICE = "{\"username\":\"alice\",\"password\":\"alice-EXAMPLE-pw\"}";

    @Test
    @DisplayName("로그인하면 세션이 생기고 /api/me 로 확인된다")
    void loginCreatesSession() throws Exception {
        var result = mvc.perform(post("/api/auth/login").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(ALICE))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.user").value("alice"))
            .andReturn();
        var session = (MockHttpSession) result.getRequest().getSession(false);
        mvc.perform(get("/api/me").session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    @DisplayName("비밀번호가 틀리면 401, 아이디 존재 여부는 알려주지 않는다")
    void wrongPassword() throws Exception {
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"alice\",\"password\":\"nope\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("invalid_credentials"));
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"ghost\",\"password\":\"nope\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("invalid_credentials"));
    }

    @Test
    @DisplayName("비밀번호 자리에 객체가 오면 400 ($ne 연산자 주입 형태)")
    void objectPasswordIsRejected() throws Exception {
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":{\"$ne\":null}}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("CSRF 토큰 없이 로그인하면 403")
    void loginWithoutCsrf() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(ALICE))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("로그인 전 세션 ID 는 로그인 후 바뀐다 (세션 고정 방어)")
    void sessionIdChanges() throws Exception {
        var session = new MockHttpSession();
        String before = session.getId();
        var result = mvc.perform(post("/api/auth/login").session(session).with(csrf())
                // 브라우저가 JSESSIONID 쿠키로 이 세션을 보냈다고 표시한다
                // (Spring 7 의 MockMvc 는 이 값이 없으면 "요청한 세션 없음"으로 보고, 세션 ID 교체도 건너뛴다)
                .with(req -> { req.setRequestedSessionId(before); return req; })
                .contentType(MediaType.APPLICATION_JSON).content(ALICE))
            .andExpect(status().isOk()).andReturn();
        String after = result.getRequest().getSession(false).getId();
        org.assertj.core.api.Assertions.assertThat(after).isNotEqualTo(before);
    }

    @Test
    @DisplayName("세션 쿠키는 HttpOnly · Secure · SameSite=Lax")
    void sessionCookieAttributes() throws Exception {
        HttpClient http = HttpClient.newHttpClient();
        String base = "http://localhost:" + port;
        // 1) CSRF 토큰 받기: 본문의 token(가린 값)을 parameterName 파라미터로, 받은 쿠키는 그대로 돌려보낸다
        //    (4-5 csrf.spa(): 가린 값은 파라미터로, 헤더에는 XSRF-TOKEN 쿠키 원본 값을 보낸다.
        //     같은 컨텍스트에서 MockMvc 의 csrf()가 CSRF 저장소를 세션 방식으로 바꿔 둘 수 있어 쿠키 대신 파라미터를 쓴다)
        HttpResponse<String> csrf = http.send(HttpRequest.newBuilder(URI.create(base + "/api/csrf")).build(),
            HttpResponse.BodyHandlers.ofString());
        String token = csrf.body().replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");
        String param = csrf.body().replaceAll(".*\"parameterName\":\"([^\"]+)\".*", "$1");
        String cookies = String.join("; ", csrf.headers().allValues("Set-Cookie").stream()
            .map(c -> c.split(";")[0]).toList());
        // 2) 로그인 → 실제 서블릿 컨테이너(Tomcat)가 붙인 Set-Cookie 를 확인
        String url = base + "/api/auth/login?" + param + "="
            + java.net.URLEncoder.encode(token, java.nio.charset.StandardCharsets.UTF_8);
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(url))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(ALICE));
        if (!cookies.isEmpty()) req.header("Cookie", cookies);
        HttpResponse<String> login = http.send(req.build(), HttpResponse.BodyHandlers.ofString());
        assertThat(login.statusCode()).isEqualTo(200);
        String sessionCookie = login.headers().allValues("Set-Cookie").stream()
            .filter(c -> c.startsWith("JSESSIONID=")).findFirst().orElseThrow();
        assertThat(sessionCookie).contains("HttpOnly").contains("Secure").containsIgnoringCase("SameSite=Lax");
    }

    @Test
    @DisplayName("로그아웃한 세션으로는 401")
    void logoutInvalidatesSession() throws Exception {
        var result = mvc.perform(post("/api/auth/login").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(ALICE))
            .andExpect(status().isOk()).andReturn();
        var session = (MockHttpSession) result.getRequest().getSession(false);
        mvc.perform(post("/api/auth/logout").session(session).with(csrf()))
            .andExpect(status().isNoContent());
        mvc.perform(get("/api/me").session(session))
            .andExpect(status().isUnauthorized());
    }
}
