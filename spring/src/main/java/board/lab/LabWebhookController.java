/*
 * ================================================================
 *  의도적으로 취약한 실습 코드 — 운영에 쓰지 마세요
 *  lab-vulnerable 프로필 + server.address=127.0.0.1 에서만 켜집니다.
 *  안전한 구현은 같은 기능의 /api/** 쪽 클래스를 보세요.
 * ================================================================
 */
package board.lab;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 4-3 웹훅: 서명·타임스탬프·멱등성 검사가 없다. 같은 요청을 다시 보내면 매번 반영된다. */
@RestController
@Profile("lab-vulnerable")
public class LabWebhookController {
    private final AtomicLong applied = new AtomicLong();

    @PostMapping("/lab/webhooks/payments")
    Map<String, Object> receive(@RequestBody Map<String, Object> event) {
        // ✗ 누가 보냈는지 확인하지 않고, 같은 이벤트도 또 처리한다
        long count = applied.incrementAndGet();
        return Map.of("status", "processed", "id", String.valueOf(event.get("id")), "appliedCount", count);
    }
}
