package board.privacy;

import java.util.List;
import org.springframework.data.repository.Repository;

/** 쓰기(save)와 읽기만. JpaRepository 를 쓰지 않아 delete·deleteAll 이 없다 (append only) */
public interface PrivacyAccessLogRepository extends Repository<PrivacyAccessLog, Long> {
    PrivacyAccessLog save(PrivacyAccessLog log);

    List<PrivacyAccessLog> findAllByOrderByIdAsc();
}
