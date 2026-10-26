package board.user;

import jakarta.persistence.*;

@Entity
@Table(name = "users")
public class AppUser {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    // 알고리즘 접두어가 붙은 해시만 저장한다 (평문 금지). {argon2}$argon2id$... 는 100자를 넘으므로 255 (3-1)
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(nullable = false, length = 20)
    private String role;

    // 4-1 프로필: 사용자가 직접 바꿀 수 있는 필드
    @Column(length = 30)
    private String nickname;

    @Column(length = 200)
    private String bio;

    // 4-1 "응답에 딸려 나가면 안 되는 내부 필드" 예시 (비밀번호 재설정 토큰, 실습용 가짜 값)
    @Column(name = "reset_token", length = 100)
    private String resetToken;

    protected AppUser() {}

    public AppUser(String username, String email, String passwordHash, String role) {
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getRole() { return role; }
    public String getNickname() { return nickname; }
    public String getBio() { return bio; }
    public String getResetToken() { return resetToken; }

    // 4-1 프로필 수정 (ProfileService 가 허용한 필드만 넘긴다)
    public void setNickname(String nickname) { this.nickname = nickname; }
    public void setBio(String bio) { this.bio = bio; }

    /**
     * 관리자 기능용. 요청 본문을 엔티티에 통째로 복사하면(BeanUtils.copyProperties) 이 setter 가
     * mass assignment 통로가 된다 → board.lab.LabProfileController (4-1 취약 실습)
     */
    public void setRole(String role) { this.role = role; }

    public void issueResetToken(String resetToken) { this.resetToken = resetToken; }

    /** 3-1 로그인 때 해시 이전 · 3-6 비밀번호 변경. 해시만 받는다. */
    public void changePasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
}
