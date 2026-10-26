package board.order;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** 실습용 주문 서비스. 상품 가격은 서버가 가진 값만 쓴다 (클라이언트가 보낸 가격을 믿지 않는다). */
@Service
public class OrderService {
    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
    static final Map<Long, Long> PRICES = Map.of(1L, 12_000L, 2L, 5_000L, 7L, 30_000L);
    private final OrderRepository repo;
    private final AtomicLong seq = new AtomicLong(-1);

    public OrderService(OrderRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public OrderDto create(String user, Long productId, int qty, String coupon) {
        Long price = PRICES.get(productId);
        if (price == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "product");
        long total = price * qty;   // qty 가 음수라면? → 경계에서 검증해야 하는 이유
        // 로그에는 식별자만 (개인정보·결제정보 X)
        log.info("order created user={} productId={} qty={}", user, productId, qty);
        Order saved = repo.save(new Order(nextId(), user, productId, qty, total, coupon));
        return OrderDto.from(saved);
    }

    /** 3-4: 주인 조건이 들어간 조회 */
    public Optional<Order> findByIdAndOwner(long id, String owner) {
        return repo.findByIdAndOwner(id, owner);
    }

    /** 주인 검사 없이 id 로만 찾는다. 검사는 호출하는 쪽(@PostAuthorize)이 한다. 없으면 403 으로 통일. */
    public OrderDto get(long id) {
        return repo.findById(id).map(OrderDto::from)
            .orElseThrow(() -> new AccessDeniedException("order"));
    }

    /** 취약 실습(/lab)용: 주인 검사 없는 조회 */
    public Optional<Order> findById(long id) {
        return repo.findById(id);
    }

    private long nextId() {
        // 실습용 단순 번호 발급 (시드 1000~1010 다음부터). 운영이라면 DB 시퀀스를 쓴다.
        seq.compareAndSet(-1, Math.max(repo.maxId(), 1010));
        return seq.incrementAndGet();
    }
}
