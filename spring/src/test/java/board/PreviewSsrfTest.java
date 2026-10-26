package board;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import board.preview.UrlGuard;
import java.net.InetAddress;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 2-6 SSRF. 외부 네트워크 없이 돈다.
 * 테스트 설정은 allowed-hosts 에 localhost 를 일부러 넣어 "이름은 통과해도 IP 에서 막히는지" 본다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PreviewSsrfTest {
    @Autowired MockMvc mvc;

    @Test
    @DisplayName("메타데이터 주소는 400")
    void metadataIsRejected() throws Exception {
        mvc.perform(get("/api/preview").param("url", "http://169.254.169.254/latest/meta-data/iam/security-credentials/"))
            .andExpect(status().isBadRequest());
        mvc.perform(get("/api/preview").param("url", "https://169.254.169.254/latest/meta-data/"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("내부 IP로 해석되는 호스트는 400")
    void hostResolvingToInternalIpIsRejected() throws Exception {
        mvc.perform(get("/api/preview").param("url", "https://localhost/admin"))
            .andExpect(status().isBadRequest());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
        "https://2852039166/", "https://0xA9FEA9FE/", "https://[::ffff:169.254.169.254]/",
        "file:///etc/passwd", "https://news.example.com@169.254.169.254/", "https://news.example.com:8443/",
        "gopher://news.example.com/"})
    @DisplayName("IP 표기 변형·다른 스킴·userinfo·포트도 400")
    void variantsAreRejected(String url) throws Exception {
        mvc.perform(get("/api/preview").param("url", url))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("해석된 IP 검사: 내부 대역은 거절, 공인 주소는 통과")
    void ipClassification() throws Exception {
        for (String ip : new String[] {"127.0.0.1", "10.0.0.5", "172.16.0.1", "192.168.0.1", "169.254.169.254",
                "100.64.0.1", "0.0.0.0", "::1", "fd00::1", "fe80::1", "::ffff:169.254.169.254"}) {
            assertThat(UrlGuard.isPublic(InetAddress.getByName(ip))).as(ip).isFalse();
        }
        assertThat(UrlGuard.isPublic(InetAddress.getByName("203.0.113.10"))).isTrue();
    }
}
