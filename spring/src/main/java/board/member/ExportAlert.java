package board.member;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** 5-5 대량 반출 알림. 기본은 경고 로그 한 줄 (운영에서는 보안 담당 채널·SIEM 으로) */
@Component
public class ExportAlert {
    private static final Logger log = LoggerFactory.getLogger(ExportAlert.class);

    public void alert(String event, String actor, long total) {
        log.warn("alert event={} actor={} total={}", event, actor, total);
    }
}
