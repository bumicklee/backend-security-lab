/*
 * ================================================================
 *  의도적으로 취약한 실습 코드 — 운영에 쓰지 마세요
 *  lab-vulnerable 프로필 + server.address=127.0.0.1 에서만 켜집니다.
 *  안전한 구현은 같은 기능의 /api/** 쪽 클래스를 보세요.
 * ================================================================
 */
package board.lab;

import board.order.OrderDto;
import board.order.OrderRequest;
import board.order.OrderService;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * 2-2 입력 검증: @Valid 가 빠져 qty=-5 같은 값이 그대로 들어간다.
 * 3-4 IDOR·BOLA: 주문 상세가 로그인·주인 확인 없이 id 만으로 조회된다.
 * 안전한 쪽은 board.order.OrderController
 */
@RestController
@Profile("lab-vulnerable")
public class LabOrderController {
    private final OrderService orders;

    public LabOrderController(OrderService orders) {
        this.orders = orders;
    }

    @PostMapping("/lab/orders")
    @ResponseStatus(HttpStatus.CREATED)
    OrderDto create(
        @RequestBody OrderRequest req) {      // @Valid 없음
      return orders.create("lab-user", req.productId(), req.qty(), req.coupon());
    }

    // 3-4 취약: 주인 조건이 없다 → 1000~1010 을 차례로 넣으면 남의 주문이 전부 보인다
    @GetMapping("/lab/orders/{id}")
    OrderDto get(@PathVariable long id) {
      return orders.findById(id)
          .map(OrderDto::from)
          .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
