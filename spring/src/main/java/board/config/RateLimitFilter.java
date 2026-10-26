package board.config;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.TimeMeter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.Principal;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 4-4 요청 제한 (Bucket4j 토큰 버킷, 분당 app.rate-limit.per-minute 회).
 *
 * <p>제한 키: 로그인 사용자 → 사용자 이름, 익명 → IP. IP 는 {@code req.getRemoteAddr()} 이고,
 * 프록시 헤더(X-Forwarded-For)는 application.yml 의 server.tomcat.remoteip.internal-proxies 에 맞는
 * 프록시가 보낸 경우에만 반영된다 → 클라이언트가 헤더를 바꿔도 키가 바뀌지 않는다.
 *
 * <p>Spring Security 필터 체인 뒤에 등록된다(기본 순서) → getUserPrincipal() 로 로그인 사용자를 본다.
 * 실습용 메모리 버킷이다. 서버가 여러 대면 Redis 등 공유 저장소(bucket4j-redis 등)로 바꾼다.
 *
 * <p>시계(TimeMeter)는 기본으로 시스템 시각이다. 테스트는 멈춘 시계 빈을 넣어, 느린 PC 에서도
 * 요청 사이에 토큰이 다시 채워지지 않게 한다(분당 100회면 0.6초마다 1개씩 채워짐).
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {
    private static final Duration ONE_MIN = Duration.ofMinutes(1);

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final long perMinute;
    private final TimeMeter clock;

    public RateLimitFilter(@Value("${app.rate-limit.per-minute:100}") long perMinute,
                           ObjectProvider<TimeMeter> clock) {
        this.perMinute = perMinute;
        this.clock = clock.getIfAvailable(() -> TimeMeter.SYSTEM_MILLISECONDS);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest req) {
        return "/health".equals(req.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        Principal p = req.getUserPrincipal();
        String key = (p != null)   // 익명은 null
            ? "user:" + p.getName()
            : "ip:" + req.getRemoteAddr();
        Bucket b = buckets.computeIfAbsent(key,
            k -> Bucket.builder().addLimit(l -> l
                .capacity(perMinute).refillGreedy(perMinute, ONE_MIN))
                .withCustomTimePrecision(clock)
                .build());
        ConsumptionProbe probe = b.tryConsumeAndReturnRemaining(1);
        if (!probe.isConsumed()) {
            long waitSec = Math.max(1, (probe.getNanosToWaitForRefill() + 999_999_999L) / 1_000_000_000L);
            res.setStatus(429);
            res.setHeader("Retry-After", String.valueOf(waitSec));
            res.setContentType("application/json");
            res.getWriter().write("{\"error\":\"too_many_requests\"}");
            return;
        }
        chain.doFilter(req, res);
    }
}
