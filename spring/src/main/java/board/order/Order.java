package board.order;

import jakarta.persistence.*;
import java.time.Instant;

/** 주문. owner 는 주문한 사용자 이름이다 (3-4 객체 수준 인가의 기준). */
@Entity
@Table(name = "orders")
public class Order {
    @Id
    private Long id;

    @Column(nullable = false, length = 50)
    private String owner;

    @Column(name = "product_id", nullable = false)
    private long productId;

    @Column(nullable = false)
    private int qty;

    @Column(nullable = false)
    private long total;

    @Column(length = 8)
    private String coupon;

    // 5-4 파기 배치: 보존 기간이 지난 주문은 구매자를 익명화한다 (이전에 만든 행은 null → 대상 아님)
    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    protected Order() {}

    public Order(long id, String owner, long productId, int qty, long total, String coupon, Instant createdAt) {
        this(id, owner, productId, qty, total, coupon);
        this.createdAt = createdAt;
    }

    public Order(long id, String owner, long productId, int qty, long total, String coupon) {
        this.id = id;
        this.owner = owner;
        this.productId = productId;
        this.qty = qty;
        this.total = total;
        this.coupon = coupon;
    }

    public Long getId() { return id; }
    public String getOwner() { return owner; }
    public long getProductId() { return productId; }
    public int getQty() { return qty; }
    public long getTotal() { return total; }
    public String getCoupon() { return coupon; }
    public Instant getCreatedAt() { return createdAt; }
}
