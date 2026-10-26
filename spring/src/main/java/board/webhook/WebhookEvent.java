package board.webhook;

import jakarta.persistence.*;
import java.time.Instant;

/** 처리한 웹훅 이벤트 기록. event_id 의 UNIQUE 제약이 멱등성의 마지막 안전망이다. */
@Entity
@Table(name = "webhook_events", uniqueConstraints = @UniqueConstraint(columnNames = "event_id"))
public class WebhookEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, length = 100)
    private String eventId;

    @Column(nullable = false, length = 50)
    private String type;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(nullable = false)
    private Long amount;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    protected WebhookEvent() {}

    public WebhookEvent(String eventId, String type, Long orderId, Long amount, Instant receivedAt) {
        this.eventId = eventId;
        this.type = type;
        this.orderId = orderId;
        this.amount = amount;
        this.receivedAt = receivedAt;
    }

    public String getEventId() { return eventId; }
}
