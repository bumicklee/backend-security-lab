package board.preview;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

/**
 * 링크 미리보기 (2-6 SSRF).
 * 허용 목록 + 해석된 IP 검사 + 리다이렉트 금지.
 * DNS 리바인딩까지 막으려면 운영에서는 전용 이그레스 프록시를 함께 쓴다.
 */
@RestController
public class PreviewController {

    private final PreviewProperties props;
    // 리다이렉트를 따르지 않는 RestClient
    private final RestClient noRedirectClient;

    public PreviewController(PreviewProperties props) {
        this.props = props;
        HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(3))
            .build();
        var factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.noRedirectClient = RestClient.builder().requestFactory(factory).build();
    }

    @GetMapping("/api/preview")
    Preview preview(@RequestParam String url) {
        URI uri = UrlGuard.check(url, props.allowedHosts());
        String html = noRedirectClient.get()
            .uri(uri).retrieve()
            // 3xx 로 내부 주소를 가리켜도 따라가지 않고 실패 처리
            .onStatus(s -> !s.is2xxSuccessful(), (req, res) -> {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY);
            })
            .body(String.class);
        return Preview.from(html);
    }
}
