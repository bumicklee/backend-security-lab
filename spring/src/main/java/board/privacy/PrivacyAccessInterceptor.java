package board.privacy;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.security.Principal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 5-4 접속기록 인터셉터 (Express lib/privacy-access.js 와 같은 다섯 항목).
 * {@link PrivacyAccess} 가 붙은 핸들러가 끝나면 일반 로그와 분리된 privacy_access_log 에 한 행을 쓴다.
 */
@Component
public class PrivacyAccessInterceptor implements HandlerInterceptor {
    private static final Logger log = LoggerFactory.getLogger(PrivacyAccessInterceptor.class);
    private final PrivacyAccessLogRepository logs;
    private final Clock clock;

    public PrivacyAccessInterceptor(PrivacyAccessLogRepository logs, Clock clock) {
        this.logs = logs;
        this.clock = clock;
    }

    @Override
    public void afterCompletion(HttpServletRequest req, HttpServletResponse res, Object handler, Exception ex) {
        if (!(handler instanceof HandlerMethod hm)) return;
        PrivacyAccess pa = hm.getMethodAnnotation(PrivacyAccess.class);
        if (pa == null) return;
        @SuppressWarnings("unchecked")
        List<Long> ids = (List<Long>) req.getAttribute(PrivacySubjects.ATTR);
        if (ids == null) ids = List.of();
        Principal who = req.getUserPrincipal();
        try {
            logs.save(new PrivacyAccessLog(
                who != null ? who.getName() : "anonymous",          // 누가
                Instant.now(clock),                                  // 언제
                req.getRemoteAddr(),                                 // 어디서
                ids.stream().map(String::valueOf).collect(Collectors.joining(",")), // 누구의
                pa.value(),                                          // 무엇을
                ex != null && res.getStatus() < 400 ? 500 : res.getStatus(),
                ids.size()));
        } catch (RuntimeException e) {
            log.error("privacy access log failed: {}", e.getMessage());
        }
    }
}
