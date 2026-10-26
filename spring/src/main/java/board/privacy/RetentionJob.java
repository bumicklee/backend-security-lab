package board.privacy;

import board.member.MemberRepository;
import board.order.OrderRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 5-4 삭제 표시가 아니라, 기한이 되면 파기. 매일 새벽 03:30.
 * 탈퇴 회원은 보존 기간(app.privacy.member-retention)이 지나면 행을 지우고,
 * 다른 법(전자상거래법 등)이 보존을 요구하는 주문은 그 기간(app.privacy.order-retention)이 지나면 구매자를 익명화한다.
 */
@Component
public class RetentionJob {
    private static final Logger log = LoggerFactory.getLogger(RetentionJob.class);
    private final MemberRepository members;
    private final OrderRepository orders;
    private final Clock clock;
    private final Duration retention;
    private final Duration orderRetention;

    public RetentionJob(MemberRepository members, OrderRepository orders, Clock clock,
            @Value("${app.privacy.member-retention}") Duration retention,
            @Value("${app.privacy.order-retention}") Duration orderRetention) {
        this.members = members;
        this.orders = orders;
        this.clock = clock;
        this.retention = retention;
        this.orderRetention = orderRetention;
    }

    @Scheduled(cron = "0 30 3 * * *")
    public void purgeExpired() {
        var cutoff = now().minus(retention);
        int n = members.purgeBefore(cutoff);
        int o = orders.anonymizeBuyerBefore(now().minus(orderRetention));
        log.info("purge members={} orders={}", n, o);
    }

    private Instant now() {
        return Instant.now(clock);
    }
}
