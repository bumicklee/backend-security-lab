package board.privacy;

import jakarta.persistence.*;
import java.time.Instant;
import org.hibernate.annotations.Immutable;

/**
 * 5-4 개인정보 접속기록: 식별자 · 접속일시 · 접속지 정보 · 처리한 정보주체 · 수행업무 (+ 결과·건수).
 * {@code @Immutable}: 한 번 쓴 행은 JPA 로 고칠 수 없다. 저장소에도 삭제 메서드가 없다.
 * 운영 DB 에서는 이 테이블에 INSERT·SELECT 권한만 주고(ops/sql/audit_schema.sql), 1년(조건에 따라 2년) 이상 보관한다.
 */
@Entity
@Immutable
@Table(name = "privacy_access_log")
public class PrivacyAccessLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_id", nullable = false, length = 100)
    private String actorId;          // 누가: 접속한 자의 식별자

    @Column(nullable = false)
    private Instant at;              // 언제: 접속 일시

    @Column(name = "from_ip", nullable = false, length = 45)
    private String fromIp;           // 어디서: 접속지 정보

    @Column(length = 20000)
    private String subjects;         // 누구의: 처리한 정보주체 (회원 id, 쉼표 구분)

    @Column(nullable = false, length = 50)
    private String task;             // 무엇을: 조회·수정·다운로드

    @Column(nullable = false)
    private int status;

    @Column(nullable = false)
    private int count;               // 처리한 정보주체 수 (대량 조회 탐지)

    protected PrivacyAccessLog() {}

    public PrivacyAccessLog(String actorId, Instant at, String fromIp, String subjects, String task, int status,
            int count) {
        this.actorId = actorId;
        this.at = at;
        this.fromIp = fromIp;
        this.subjects = subjects;
        this.task = task;
        this.status = status;
        this.count = count;
    }

    public Long getId() { return id; }
    public String getActorId() { return actorId; }
    public Instant getAt() { return at; }
    public String getFromIp() { return fromIp; }
    public String getSubjects() { return subjects; }
    public String getTask() { return task; }
    public int getStatus() { return status; }
    public int getCount() { return count; }
}
