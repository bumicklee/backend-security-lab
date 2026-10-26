package board;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 4-4 요청 제한 (RateLimitFilter · Bucket4j, 분당 100회).
 * 버킷은 컨텍스트 안에서 공유되므로 테스트마다 다른 키(IP·사용자)를 쓴다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "app.rate-limit.per-minute=100")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RateLimitFilterTest {
    @Autowired MockMvc mvc;
    @LocalServerPort int port;

    static MockHttpServletRequestBuilder from(String ip) {
        return get("/api/posts").with(r -> { r.setRemoteAddr(ip); return r; });
    }

    @Test
    @DisplayName("X-Forwarded-For를 바꿔도 101번째 요청은 429")
    void spoofedXffDoesNotHelp() throws Exception {
        // 실제 Tomcat 으로 보낸다: 127.0.0.1 은 internal-proxies(10.0.0.x)가 아니라 XFF 를 믿지 않는다
        HttpClient http = HttpClient.newHttpClient();
        int last = 0;
        for (int i = 1; i <= 101; i++) {
            HttpRequest req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/posts"))
                .header("X-Forwarded-For", "198.51.100." + i).build();
            last = http.send(req, HttpResponse.BodyHandlers.discarding()).statusCode();
            if (i <= 100) assertThat(last).as("요청 %d", i).isEqualTo(200);
        }
        assertThat(last).isEqualTo(429);
    }

    @Test
    @DisplayName("429 응답에 Retry-After가 있다")
    void retryAfter() throws Exception {
        for (int i = 0; i < 100; i++) mvc.perform(from("198.51.100.20")).andExpect(status().isOk());
        mvc.perform(from("198.51.100.20"))
            .andExpect(status().isTooManyRequests())
            .andExpect(header().exists("Retry-After"))
            .andExpect(jsonPath("$.error").value("too_many_requests"));
    }

    @Test
    @DisplayName("익명은 IP 로, 로그인 사용자는 사용자 이름으로 따로 센다")
    void keyIsPrincipalThenIp() throws Exception {
        for (int i = 0; i < 100; i++) mvc.perform(from("198.51.100.30")).andExpect(status().isOk());
        mvc.perform(from("198.51.100.30")).andExpect(status().isTooManyRequests());
        // 같은 IP 라도 로그인 사용자는 사용자 키, 다른 IP 의 익명은 다른 키
        mvc.perform(from("198.51.100.30").with(user("carol"))).andExpect(status().isOk());
        mvc.perform(from("198.51.100.31")).andExpect(status().isOk());
    }
}
