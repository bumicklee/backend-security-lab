package board.detect;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 6-2 계정별 조회량 감시. 키는 IP 가 아니라 "계정 + 날짜(UTC)".
 * 300번째 요청에서 알림(bulk_lookup) 한 번, 1000 번을 넘으면 429, 처음 막을 때(1001번째) 차단 이벤트(bulk_block) 한 번.
 * 기준값은 예시다. 실제 사용자 분포를 몇 주 지켜본 뒤 정한다. 주소 조회 경로에만 등록한다(DetectWebConfig).
 */
@Component
public record BulkLookupGuard(LookupCounter redis, SecurityEvents events, Clock clock)
        implements HandlerInterceptor {
    public static final long ALERT_AT = 300;
    public static final long BLOCK_OVER = 1000;

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) throws Exception {
        String user = req.getUserPrincipal().getName();
        String key = "lookup:" + user + ":" + LocalDate.now(clock.withZone(ZoneOffset.UTC));
        long n = redis.increment(key);                                      // 계정별 하루 조회 수
        if (n == 1) redis.expire(key, Duration.ofDays(2));
        if (n == ALERT_AT) events.publish("bulk_lookup", user, req.getRemoteAddr(), n);       // 알림은 한 번
        if (n == BLOCK_OVER + 1) events.publish("bulk_block", user, req.getRemoteAddr(), n);  // 차단도 기록
        if (n > BLOCK_OVER) {                                               // 확실한 신호만 자동 차단
            res.setStatus(429);
            res.setContentType("application/json");
            res.getWriter().write("{\"error\":\"review_required\"}");
            return false;
        }
        return true;
    }
}
