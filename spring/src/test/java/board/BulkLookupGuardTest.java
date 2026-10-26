package board;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import board.detect.MemoryLookupCounter;
import board.detect.SecurityEvents;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 6-2 계정별 조회량 감시: 300번째 알림 한 번, 1000번 초과 429, 1001번째 차단 이벤트 한 번 */
@SpringBootTest
@AutoConfigureMockMvc @ActiveProfiles("test")
class BulkLookupGuardTest {
    @Autowired MockMvc mvc;
    @Autowired SecurityEvents events;
    @Autowired MemoryLookupCounter counter;
    final JsonMapper json = JsonMapper.builder().build();

    static String today() {
        return LocalDate.now(ZoneOffset.UTC).toString();
    }

    List<Map<String, Object>> eventsFor(String user) {
        return events.recent().stream().filter(e -> user.equals(e.get("user"))).toList();
    }

    @Test
    @DisplayName("천한 번 조회하면 천 번은 200, 마지막 한 번은 429 · 알림 한 번, 차단 한 번")
    void thousandAndOne() throws Exception {
        Map<Integer, Integer> codes = new TreeMap<>();
        for (int i = 1; i <= 1001; i++) {
            int s = mvc.perform(get("/api/addresses").param("page", String.valueOf(i))
                    .with(user("u-90001")).with(r -> { r.setRemoteAddr("198.51.100.20"); return r; }))
                .andReturn().getResponse().getStatus();
            codes.merge(s, 1, Integer::sum);
        }
        assertThat(codes).isEqualTo(Map.of(200, 1000, 429, 1));
        List<Map<String, Object>> ev = eventsFor("u-90001");
        assertThat(ev).extracting(e -> e.get("event")).containsExactly("bulk_lookup", "bulk_block");
        assertThat(ev).extracting(e -> e.get("count")).containsExactly(300L, 1001L);
        assertThat(ev).allSatisfy(e -> {
            assertThat(e.get("ip")).isEqualTo("198.51.100.20");
            assertThat((String) e.get("ts")).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z");
        });
        // security.log: 줄마다 JSON, ts 가 첫 필드 (fail2ban·jq 가 읽는 모양)
        List<String> lines = Files.readAllLines(events.file()).stream().filter(l -> l.contains("u-90001")).toList();
        assertThat(lines).hasSize(2);
        JsonNode last = json.readTree(lines.get(1));
        assertThat(last.propertyNames()).containsExactly("ts", "event", "user", "ip", "count");
        assertThat(last.get("event").asString()).isEqualTo("bulk_block");
    }

    @Test
    @DisplayName("이미 1,000번 넘은 계정은 계속 429 review_required, 이벤트는 다시 남기지 않는다")
    void staysBlocked() throws Exception {
        counter.set("lookup:carol-test:" + today(), 1001);
        for (int i = 0; i < 3; i++) {
            mvc.perform(get("/api/addresses").with(user("carol-test")))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("review_required"));
        }
        assertThat(eventsFor("carol-test")).isEmpty();
        mvc.perform(get("/api/addresses").with(user("alice"))).andExpect(status().isOk())
            .andExpect(jsonPath("$[0].label").value("집"));
    }

    @Test
    @DisplayName("로그인하지 않으면 401, 다른 경로에는 감시가 걸리지 않는다")
    void onlyAddresses() throws Exception {
        mvc.perform(get("/api/addresses")).andExpect(status().isUnauthorized());
        counter.set("lookup:dave-test:" + today(), 5000);
        mvc.perform(get("/api/posts").with(user("dave-test"))).andExpect(status().isOk());
    }

    @Test
    @DisplayName("로그인한 사용자의 403 은 authz_denied 보안 이벤트로 남는다")
    void authzDenied() throws Exception {
        mvc.perform(get("/api/admin/members/export").with(user("erin-test"))
                .with(r -> { r.setRemoteAddr("203.0.113.77"); return r; }))
            .andExpect(status().isForbidden());
        assertThat(eventsFor("erin-test")).singleElement().satisfies(e -> {
            assertThat(e.get("event")).isEqualTo("authz_denied");
            assertThat(e.get("ip")).isEqualTo("203.0.113.77");
        });
    }
}
