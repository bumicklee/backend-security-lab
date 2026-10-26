package board.payment;

import board.support.Mask;
import jakarta.validation.Valid;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 결제 수단 등록 (앱 연동 API). 로그에는 끝 4자리만 남긴다 (0-3). */
@RestController
public class PaymentController {
    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    @PostMapping("/api/payments")
    Map<String, String> register(@Valid @RequestBody Payment payment, Authentication auth) {
        return switch (payment) {
            case Payment.Card c -> {
                log.info("payment registered user={} type=card card={}", auth.getName(), Mask.last4(c.cardNo()));
                yield Map.of("type", "card", "masked", Mask.last4(c.cardNo()));
            }
            case Payment.Bank b -> {
                log.info("payment registered user={} type=bank account={}", auth.getName(), Mask.last4(b.accountNo()));
                yield Map.of("type", "bank", "masked", Mask.last4(b.accountNo()));
            }
        };
    }
}
