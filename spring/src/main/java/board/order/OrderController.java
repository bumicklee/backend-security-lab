package board.order;

import static org.springframework.http.HttpStatus.FORBIDDEN;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class OrderController {
    private final OrderService orders;

    public OrderController(OrderService orders) {
        this.orders = orders;
    }

    // @Valid 가 있어야 DTO 의 애너테이션이 실제로 검사된다
    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    OrderDto create(
        @Valid @RequestBody OrderRequest req, Authentication auth) {
      return orders.create(auth.getName(), req.productId(), req.qty(), req.coupon());
    }

    // 3-4 객체 수준 인가(BOLA): 남의 주문·없는 주문 모두 403
    @GetMapping("/orders/{id}")
    OrderDto get(@PathVariable long id,
                 Authentication auth) {
      // 주인 조건을 쿼리에 함께 넣는다
      return orders
          .findByIdAndOwner(id, auth.getName())
          .map(OrderDto::from)
          .orElseThrow(() ->
              new ResponseStatusException(
                  FORBIDDEN));
    }

    // 3-4 같은 검사를 메서드 보안으로: 꺼낸 결과의 주인이 로그인한 사용자가 아니면 403
    @PostAuthorize("returnObject.user() == authentication.name")    // 객체 수준: 주인인가
    @GetMapping("/v2/orders/{id}")
    OrderDto getV2(@PathVariable long id) { return orders.get(id); }
}
