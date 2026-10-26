package board.order;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface OrderRepository extends JpaRepository<Order, Long> {
    /** 3-4: 주인 조건을 쿼리에 함께 넣는다 → 남의 주문은 "없음"과 똑같이 보인다 */
    Optional<Order> findByIdAndOwner(long id, String owner);

    @Query("select coalesce(max(o.id), 0) from Order o")
    long maxId();

    /** 5-4 보존 기간이 지난 주문은 구매자를 익명화한다 (매출 기록은 남기고 누구인지는 지운다) */
    @Modifying
    @Transactional
    @Query("update Order o set o.owner = 'anonymized' where o.createdAt < :cutoff and o.owner <> 'anonymized'")
    int anonymizeBuyerBefore(@Param("cutoff") Instant cutoff);
}
