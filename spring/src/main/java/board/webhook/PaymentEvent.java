package board.webhook;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

/** 결제사가 보내는 이벤트 본문 (가짜 형식). */
public record PaymentEvent(
    @NotBlank @Pattern(regexp = "^evt_[A-Za-z0-9]{1,64}$") String id,
    @NotBlank @Pattern(regexp = "^payment\\.(succeeded|failed|refunded)$") String type,
    @NotNull @Positive Long orderId,
    @NotNull @Positive Long amount) {}
