package board.detect;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** 6-2 실습용 서버 메모리 카운터 (키별 만료 시각 포함). */
@Component
public class MemoryLookupCounter implements LookupCounter {
    private record Entry(long n, Instant expiresAt) {}

    private final Map<String, Entry> m = new ConcurrentHashMap<>();
    private final Clock clock;

    public MemoryLookupCounter(Clock clock) {
        this.clock = clock;
    }

    @Override
    public long increment(String key) {
        Instant now = clock.instant();
        m.values().removeIf(e -> e.expiresAt() != null && !e.expiresAt().isAfter(now)); // 지난 키 정리
        return m.merge(key, new Entry(1, null), (old, one) -> new Entry(old.n() + 1, old.expiresAt())).n();
    }

    @Override
    public void expire(String key, Duration ttl) {
        m.computeIfPresent(key, (k, e) -> new Entry(e.n(), clock.instant().plus(ttl)));
    }

    /** 테스트용: 오늘 이미 n 번 조회한 상태로 만든다 */
    public void set(String key, long n) {
        m.put(key, new Entry(n, clock.instant().plus(Duration.ofDays(2))));
    }
}
