package board.user;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 3-5: 계정(사용자 이름) 기준 로그인 실패 누적 → 대기 시간.
 * 5번째 실패부터 1·2·4…분, 최대 15분. 계정이 없어도 똑같이 센다(같은 응답·같은 대기).
 * 실습용 메모리 저장소. 서버가 여러 대라면 Redis 등 공유 저장소에 둔다.
 */
@Component
public class LoginAttempts {
    static final int FREE_ATTEMPTS = 4;              // 4번까지는 바로 다시 시도 가능
    static final Duration MAX_WAIT = Duration.ofMinutes(15);

    private record State(int failures, Instant lockedUntil) {}

    private final Map<String, State> states = new ConcurrentHashMap<>();
    private final Clock clock;

    public LoginAttempts(Clock clock) {
        this.clock = clock;
    }

    /** 대소문자만 다른 이름으로 우회하지 못하게 정규화한 값을 키로 쓴다 */
    static String key(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }

    /** 0 이면 바로 시도 가능, 아니면 남은 대기 시간(초, 올림) */
    public long retryAfterSec(String username) {
        State s = states.get(key(username));
        if (s == null || s.lockedUntil() == null) return 0;
        long ms = Duration.between(clock.instant(), s.lockedUntil()).toMillis();
        return ms <= 0 ? 0 : (ms + 999) / 1000;
    }

    public void fail(String username) {
        states.compute(key(username), (k, s) -> {
            int failures = (s == null ? 0 : s.failures()) + 1;
            Instant lockedUntil = null;
            if (failures > FREE_ATTEMPTS) {
                // 5번째 실패 → 1분, 6번째 → 2분, 7번째 → 4분 … 최대 15분
                long minutes = 1L << Math.min(failures - FREE_ATTEMPTS - 1, 4);
                Duration wait = Duration.ofMinutes(minutes);
                if (wait.compareTo(MAX_WAIT) > 0) wait = MAX_WAIT;
                lockedUntil = clock.instant().plus(wait);
            }
            return new State(failures, lockedUntil);
        });
    }

    public void reset(String username) {
        states.remove(key(username));
    }
}
