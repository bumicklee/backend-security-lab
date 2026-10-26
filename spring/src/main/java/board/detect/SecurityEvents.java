package board.detect;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * 6-2 보안 이벤트 로그(security.log). 한 줄에 JSON 하나, 첫 필드는 ISO 8601 UTC 시각 {@code "ts"}.
 * fail2ban 은 줄에서 시각을 찾아 findtime 을 계산하므로 시각이 없으면 매칭되지 않는다.
 * 필드 순서 ts → event → user → ip → count 는 ops/fail2ban/filter.d/board-security.conf 의 failregex 와 맞춘다.
 * 파일(app.security-log.file)을 비워 두면 로그(WARN)로만 남긴다.
 */
@Component
public class SecurityEvents {
    private static final Logger log = LoggerFactory.getLogger("SECURITY");
    private final JsonMapper json = JsonMapper.builder().build();
    private final List<Map<String, Object>> recent = new CopyOnWriteArrayList<>();
    private final Path file;
    private final Clock clock;

    public SecurityEvents(@Value("${app.security-log.file:}") String file, Clock clock) {
        this.file = file.isBlank() ? null : Path.of(file);
        this.clock = clock;
    }

    public void publish(String event, String user, String ip) {
        publish(event, user, ip, null);
    }

    public synchronized void publish(String event, String user, String ip, Long count) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("ts", Instant.now(clock).truncatedTo(ChronoUnit.SECONDS).toString());
        e.put("event", event);
        e.put("user", user);
        e.put("ip", ip);
        if (count != null) e.put("count", count);
        String line = json.writeValueAsString(e);
        recent.add(e);
        if (recent.size() > 1000) recent.removeFirst();
        log.warn(line);
        if (file == null) return;
        try {
            if (file.getParent() != null) Files.createDirectories(file.getParent());
            Files.writeString(file, line + "\n", StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.WRITE);
        } catch (IOException ex) {
            log.error("security log write failed: {}", ex.getMessage());
        }
    }

    /** 테스트·점검용: 최근 이벤트 (최대 1,000개) */
    public List<Map<String, Object>> recent() {
        return List.copyOf(recent);
    }

    public Path file() {
        return file;
    }
}
