package board.member;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 5-5 반출 감시: 관리자별 "오늘 누적 내보내기 건수". 실습용 서버 메모리.
 * 서버가 여러 대면 Redis INCRBY + 하루 만료로 같은 일을 한다.
 */
@Component
public class ExportCounter {
    private final Map<String, Long> totals = new ConcurrentHashMap<>();
    private final Clock clock;

    public ExportCounter(Clock clock) {
        this.clock = clock;
    }

    public long add(String user, long n) {
        String today = LocalDate.now(clock).toString();
        totals.keySet().removeIf(k -> !k.endsWith(today)); // 지난 날짜 정리
        return totals.merge(user + ":" + today, n, Long::sum);
    }
}
