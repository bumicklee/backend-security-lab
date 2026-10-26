package board.member;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * 회원(게시판 이용자 연락처). 5-2 전화번호는 평문 컬럼 없이 봉투 암호화(phone_ct · phone_iv · phone_key)와
 * 검색용 블라인드 인덱스(phone_idx)로만 저장한다. 5-4 탈퇴 후 보존 기간이 지나면 파기 배치가 지운다.
 */
@Entity
@Table(name = "members", indexes = @Index(name = "idx_members_phone_idx", columnList = "phone_idx"))
public class Member {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String nickname;

    @Column(nullable = false, length = 100)
    private String email;

    @Column(name = "phone_ct", length = 512)
    private byte[] phoneCt;          // 암호문 + GCM 태그(16바이트)

    @Column(name = "phone_iv", length = 12)
    private byte[] phoneIv;

    @Column(name = "phone_key", length = 1024)
    private byte[] phoneKey;         // KMS 가 감싼 데이터 키

    @Column(name = "phone_idx", length = 64)
    private String phoneIdx;         // HMAC-SHA256(인덱스 키, 번호)

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "withdrawn_at")
    private Instant withdrawnAt;

    protected Member() {}

    public Member(String nickname, String email) {
        this.nickname = nickname;
        this.email = email;
    }

    public void phone(byte[] ct, byte[] iv, byte[] wrappedKey, String idx) {
        this.phoneCt = ct;
        this.phoneIv = iv;
        this.phoneKey = wrappedKey;
        this.phoneIdx = idx;
    }

    public void withdraw(Instant at) { this.withdrawnAt = at; }

    public Long getId() { return id; }
    public String getNickname() { return nickname; }
    public String getEmail() { return email; }
    public byte[] getPhoneCt() { return phoneCt; }
    public byte[] getPhoneIv() { return phoneIv; }
    public byte[] getPhoneKey() { return phoneKey; }
    public String getPhoneIdx() { return phoneIdx; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getWithdrawnAt() { return withdrawnAt; }
}
