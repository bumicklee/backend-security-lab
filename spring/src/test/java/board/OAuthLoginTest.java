package board;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 3-3 OAuth 2.0 · OIDC 로그인. 실제 IdP 없이 확인하려고 discovery(issuer-uri) 대신
 * 엔드포인트를 직접 적은 가짜 등록(accounts.example.com)을 쓴다. 네트워크는 쓰지 않는다.
 */
@SpringBootTest(properties = {
    "spring.security.oauth2.client.registration.idp.client-id=board-web",
    "spring.security.oauth2.client.registration.idp.client-secret=EXAMPLE-only",
    "spring.security.oauth2.client.registration.idp.scope=openid,profile,email",
    "spring.security.oauth2.client.registration.idp.authorization-grant-type=authorization_code",
    "spring.security.oauth2.client.registration.idp.redirect-uri={baseUrl}/login/oauth2/code/{registrationId}",
    "spring.security.oauth2.client.provider.idp.authorization-uri=https://accounts.example.com/authorize",
    "spring.security.oauth2.client.provider.idp.token-uri=https://accounts.example.com/oauth2/token",
    "spring.security.oauth2.client.provider.idp.jwk-set-uri=https://accounts.example.com/jwks",
    "spring.security.oauth2.client.provider.idp.user-name-attribute=sub",
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OAuthLoginTest {
    @Autowired MockMvc mvc;

    /** 브라우저가 https://board.example.com 으로 접속했다고 표시한다 */
    static final RequestPostProcessor BOARD = req -> {
        req.setScheme("https");
        req.setServerName("board.example.com");
        req.setServerPort(443);
        req.setSecure(true);
        return req;
    };

    static Map<String, String> query(String url) {
        return UriComponentsBuilder.fromUriString(url).build(true).getQueryParams().toSingleValueMap()
            .entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @Test
    @DisplayName("로그인 시작 주소에 state·nonce·code_challenge(S256)가 붙는다")
    void authorizationRequestHasStateNoncePkce() throws Exception {
        String location = mvc.perform(get("/oauth2/authorization/idp").with(BOARD))
            .andExpect(status().is3xxRedirection())
            .andReturn().getResponse().getRedirectedUrl();

        assertThat(location).startsWith("https://accounts.example.com/authorize?");
        Map<String, String> q = query(location);
        assertThat(q).containsKeys("state", "nonce", "code_challenge");
        assertThat(q.get("code_challenge_method")).isEqualTo("S256");
        assertThat(q.get("response_type")).isEqualTo("code");
        // 등록한 값과 글자 그대로 일치하는 redirect_uri
        assertThat(q.get("redirect_uri")).isEqualTo("https://board.example.com/login/oauth2/code/idp");
    }

    @Test
    @DisplayName("저장된 요청과 state가 다른 콜백은 로그인되지 않는다")
    void callbackWithWrongStateIsRejected() throws Exception {
        var session = new MockHttpSession();
        mvc.perform(get("/oauth2/authorization/idp").with(BOARD).session(session))
            .andExpect(status().is3xxRedirection());

        // 공격자가 자기 code 와 다른 state 로 콜백을 보내게 만든 상황
        mvc.perform(get("/login/oauth2/code/idp").with(BOARD).session(session)
                .param("code", "attacker-code").param("state", "not-the-saved-state"))
            .andExpect(status().is3xxRedirection());   // 실패 처리(/login?error)로 보낸다

        assertThat(session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY)).isNull();
        mvc.perform(get("/api/me").with(BOARD).session(session)).andExpect(status().isUnauthorized());
    }
}
