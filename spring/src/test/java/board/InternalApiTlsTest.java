package board;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import board.tls.InternalApiConfig;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.net.ssl.X509TrustManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

/** 5-1 외부 호출의 인증서 검증: 끄지 말고, 내부 API 클라이언트에만 사내 CA(SSL 번들)를 지정한다 */
@SpringBootTest(properties = {
    "spring.ssl.bundle.pem.internal.truststore.certificate=classpath:tls/internal-ca.pem",
    "app.internal-api.base-url=https://pay.internal.example" })
@ActiveProfiles("test")
class InternalApiTlsTest {
    @Autowired SslBundles ssl;
    @Autowired RestClient internalApi;

    static X509Certificate cert(String name) throws Exception {
        try (InputStream in = InternalApiTlsTest.class.getResourceAsStream("/tls/" + name)) {
            return (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(in);
        }
    }

    X509TrustManager trustManager() {
        return Arrays.stream(ssl.getBundle("internal").getManagers().getTrustManagers())
            .filter(X509TrustManager.class::isInstance).map(X509TrustManager.class::cast).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("internal 번들은 기본 신뢰 목록 대신 사내 CA 하나만 믿는다")
    void bundleTrustsOnlyInternalCa() throws Exception {
        assertThat(internalApi).isNotNull();
        assertThat(trustManager().getAcceptedIssuers()).containsExactly(cert("internal-ca.pem"));
    }

    @Test
    @DisplayName("사내 CA 가 서명한 인증서는 통과, 같은 이름의 중간자 인증서는 핸드셰이크에서 거절")
    void rejectsMitmCertificate() throws Exception {
        X509TrustManager tm = trustManager();
        tm.checkServerTrusted(new X509Certificate[] { cert("internal-leaf.pem") }, "ECDHE_ECDSA");
        X509Certificate mitm = cert("internal-mitm.pem");
        assertThat(mitm.getSubjectX500Principal()).isEqualTo(cert("internal-leaf.pem").getSubjectX500Principal());
        assertThatThrownBy(() -> tm.checkServerTrusted(new X509Certificate[] { mitm }, "ECDHE_ECDSA"))
            .isInstanceOf(CertificateException.class);
    }

    @Test
    @DisplayName("base-url 이 없으면 내부 API 클라이언트를 만들지 않는다 (기본 실행은 네트워크를 쓰지 않음)")
    void optionalWithoutConfig() {
        new ApplicationContextRunner().withUserConfiguration(InternalApiConfig.class)
            .run(ctx -> assertThat(ctx).doesNotHaveBean("internalApi"));
    }

    @Test
    @DisplayName("코드에 인증서 검증 끄기(모두 믿는 TrustManager)가 없다")
    void noTrustAllCode() throws IOException {
        Pattern trustAll = Pattern.compile(
            "checkServerTrusted\\([^)]*\\)\\s*(throws[^{]+)?\\{\\s*\\}|NoopHostnameVerifier|TrustAllStrategy|InsecureTrustManagerFactory");
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            List<Path> bad = files.filter(p -> p.toString().endsWith(".java"))
                .filter(p -> { try { return trustAll.matcher(Files.readString(p)).find(); } catch (IOException e) { return true; } })
                .toList();
            assertThat(bad).isEmpty();
        }
    }
}
