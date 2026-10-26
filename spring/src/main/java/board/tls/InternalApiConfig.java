package board.tls;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.restclient.autoconfigure.RestClientSsl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * 5-1 내부 API 클라이언트: 인증서 검증을 끄지 않고(모두 믿는 TrustManager 금지), 이 클라이언트에만 사내 CA 를 지정한다.
 *
 * <pre>
 * # application-internal-api.yml: SSL 번들
 * spring.ssl.bundle.pem.internal:
 *   truststore.certificate: classpath:ca.pem
 * </pre>
 *
 * 번들의 truststore 는 JDK 기본 신뢰 목록을 대신한다 → 이 RestClient 는 사내 CA 가 서명한 인증서만 믿는다.
 * {@code app.internal-api.base-url} 이 있을 때만 만든다 (기본 실행·테스트는 네트워크를 쓰지 않는다).
 */
@Configuration
@ConditionalOnProperty("app.internal-api.base-url")
public class InternalApiConfig {

    @Bean
    RestClient internalApi(RestClient.Builder builder, RestClientSsl ssl,
            @Value("${app.internal-api.base-url}") String baseUrl) {
        if (!baseUrl.startsWith("https://")) {
            throw new IllegalStateException("내부 API 도 https 로만 호출합니다: app.internal-api.base-url");
        }
        return builder.baseUrl(baseUrl).apply(
            ssl.fromBundle("internal")).build();
    }
}
