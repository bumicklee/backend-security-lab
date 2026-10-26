package board.webhook;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;
import java.time.Clock;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 결제 웹훅 수신 (4-3, 4-4).
 * 1) 서명·타임스탬프 검증 → 실패 401
 * 2) 이벤트 ID 로 멱등 처리 → 같은 이벤트가 다시 와도 한 번만 반영
 */
@RestController
public class WebhookController {
    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final WebhookVerifier verifier;
    private final WebhookEventRepository events;
    private final ObjectMapper om;
    private final Validator validator;
    private final Clock clock;

    public WebhookController(WebhookVerifier verifier, WebhookEventRepository events,
            ObjectMapper om, Validator validator, Clock clock) {
        this.verifier = verifier;
        this.events = events;
        this.om = om;
        this.validator = validator;
        this.clock = clock;
    }

    // 서명은 "받은 바이트 그대로" 계산해야 하므로 byte[] 로 받는다
    @PostMapping(value = "/api/webhooks/payments", consumes = "application/json")
    ResponseEntity<Map<String, String>> receive(@RequestBody byte[] body,
            @RequestHeader(value = "X-Webhook-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "X-Webhook-Signature", required = false) String signature) {
        if (!verifier.verify(body, timestamp, signature)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "invalid_signature"));
        }
        PaymentEvent event;
        try {
            event = om.readValue(body, PaymentEvent.class);
        } catch (JacksonException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "invalid_body"));
        }
        if (!validator.validate(event).isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "invalid_body"));
        }
        if (events.existsByEventId(event.id())) {
            // 이미 처리한 이벤트: 다시 반영하지 않고 2xx 로 응답해 재시도를 멈춘다
            return ResponseEntity.ok(Map.of("status", "duplicate", "id", event.id()));
        }
        try {
            events.saveAndFlush(new WebhookEvent(event.id(), event.type(), event.orderId(), event.amount(),
                clock.instant()));
        } catch (DataIntegrityViolationException race) {
            return ResponseEntity.ok(Map.of("status", "duplicate", "id", event.id()));
        }
        log.info("webhook processed id={} type={} orderId={}", event.id(), event.type(), event.orderId());
        return ResponseEntity.ok(Map.of("status", "processed", "id", event.id()));
    }
}
