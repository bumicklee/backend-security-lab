package board.member;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface MemberRepository extends JpaRepository<Member, Long> {
    /** 5-2 조회: WHERE phone_idx = ? (평문 번호로는 찾지 않는다) */
    List<Member> findByPhoneIdxAndWithdrawnAtIsNullOrderByIdAsc(String phoneIdx);

    Optional<Member> findByIdAndWithdrawnAtIsNull(long id);

    /** 5-5 내보내기: 1,000건씩, id 오름차순 (Pageable 로 전달) */
    List<Member> findByWithdrawnAtIsNull(Pageable page);

    /** 5-4 파기: 탈퇴 후 보존 기간이 지난 회원은 행을 지운다 (삭제 표시가 아니라) */
    @Modifying
    @Transactional
    @Query("delete from Member m where m.withdrawnAt < :cutoff")
    int purgeBefore(@Param("cutoff") Instant cutoff);
}
