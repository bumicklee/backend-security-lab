/*
 * ================================================================
 *  의도적으로 취약한 실습 코드 — 운영에 쓰지 마세요
 *  lab-vulnerable 프로필 + server.address=127.0.0.1 에서만 켜집니다.
 *  안전한 구현은 같은 기능의 /api/** 쪽 클래스를 보세요.
 * ================================================================
 */
package board.lab;

import board.preview.Preview;
import java.net.http.HttpClient;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

/** 2-6 SSRF: 받은 주소로 그대로 요청, 리다이렉트도 따라간다. 안전한 쪽은 board.preview.PreviewController */
@RestController
@Profile("lab-vulnerable")
public class LabPreviewController {
    private final RestClient client = RestClient.builder()
        .requestFactory(new JdkClientHttpRequestFactory(
            HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()))
        .build();

    @GetMapping("/lab/preview")
    Preview preview(@RequestParam String url) {
      // 받은 주소로 그대로 요청한다
      String html = client.get()
          .uri(url)
          .retrieve()
          .body(String.class);
      return Preview.from(html);
    }
}
