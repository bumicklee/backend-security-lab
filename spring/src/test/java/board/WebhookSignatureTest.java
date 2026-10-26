package board;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import board.webhook.WebhookEventRepository;
import board.webhook.WebhookVerifier;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** 4-3 웹훅 서명 검증과 재전송 방어 (application-test.yml 의 가짜 시크릿 사용) */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WebhookSignatureTest {
    static final String SECRET = "whsec_TEST_ONLY";

    @Autowired MockMvc mvc;
    @Autowired WebhookEventRepository events;

    static String event(String id) {
        return "{\"id\":\"" + id + "\",\"type\":\"payment.succeeded\",\"orderId\":42,\"amount\":12000}";
    }

    static String newId() {
        return "evt_" + UUID.randomUUID().toString().replace("-", "");
    }

    ResultActions send(String body, String ts, String sig) throws Exception {
        var req = post("/api/webhooks/payments").contentType(MediaType.APPLICATION_JSON).content(body);
        if (ts != null) req.header("X-Webhook-Timestamp", ts);
        if (sig != null) req.header("X-Webhook-Signature", sig);
        return mvc.perform(req);
    }

    static String sign(String ts, String body) {
        return WebhookVerifier.sign(SECRET, ts, body.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("올바른 서명은 200 processed")
    void validSignature() throws Exception {
        String body = event(newId());
        String ts = String.valueOf(Instant.now().getEpochSecond());
        send(body, ts, sign(ts, body)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("processed"));
    }

    @Test
    @DisplayName("서명이 틀리거나 없으면 401")
    void badSignature() throws Exception {
        String body = event(newId());
        String ts = String.valueOf(Instant.now().getEpochSecond());
        send(body, ts, "sha256=" + "0".repeat(64)).andExpect(status().isUnauthorized());
        send(body, ts, null).andExpect(status().isUnauthorized());
        // 다른 시크릿으로 만든 서명
        send(body, ts, WebhookVerifier.sign("whsec_attacker", ts, body.getBytes(StandardCharsets.UTF_8)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("본문을 한 글자라도 바꾸면 401")
    void tamperedBody() throws Exception {
        String body = event(newId());
        String ts = String.valueOf(Instant.now().getEpochSecond());
        String sig = sign(ts, body);
        send(body.replace("12000", "1"), ts, sig).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("5분 지난 요청을 다시 보내면 401 (재전송 차단)")
    void oldTimestampIsRejected() throws Exception {
        String body = event(newId());
        String ts = String.valueOf(Instant.now().minusSeconds(600).getEpochSecond());
        send(body, ts, sign(ts, body)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("같은 이벤트를 다시 보내도 한 번만 반영된다 (멱등성)")
    void replayWithinWindowIsNotAppliedTwice() throws Exception {
        String id = newId();
        String body = event(id);
        String ts = String.valueOf(Instant.now().getEpochSecond());
        String sig = sign(ts, body);
        send(body, ts, sig).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("processed"));
        send(body, ts, sig).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("duplicate"));
        assertThat(events.findAll().stream().filter(e -> e.getEventId().equals(id))).hasSize(1);
    }
}
