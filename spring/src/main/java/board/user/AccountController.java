package board.user;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.password.CompromisedPasswordChecker;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/**
 * 3-6: 비밀번호를 바꾸면 그 사용자의 모든 세션을 끊는다 ("모든 기기 로그아웃").
 * 훔친 세션 쿠키로 버티던 공격자도 여기서 끊긴다.
 */
@RestController
@RequestMapping("/api/me")
public class AccountController {

    public record ChangePasswordRequest(
        @NotBlank @Size(max = 200) String currentPassword,
        @NotBlank @Size(min = 12, max = 200) String newPassword) {}

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final CompromisedPasswordChecker compromised;
    private final PrincipalSessionRepository sessions;

    public AccountController(AppUserRepository users, PasswordEncoder encoder,
            CompromisedPasswordChecker compromised, PrincipalSessionRepository sessions) {
        this.users = users;
        this.encoder = encoder;
        this.compromised = compromised;
        this.sessions = sessions;
    }

    @PostMapping("/password")
    @Transactional
    public ResponseEntity<?> changePassword(@Valid @RequestBody ChangePasswordRequest req, Authentication auth) {
        AppUser u = users.findByUsername(auth.getName()).orElseThrow();
        if (!encoder.matches(req.currentPassword(), u.getPasswordHash())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "invalid_current_password"));
        }
        // 3-5: 새 비밀번호도 유출 목록과 대조한다
        if (compromised.check(req.newPassword()).isCompromised()) {
            return ResponseEntity.badRequest().body(Map.of("error", "compromised_password"));
        }
        u.changePasswordHash(encoder.encode(req.newPassword()));
        revokeAll(u.getUsername());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/sessions/revoke-all")
    public ResponseEntity<Void> revokeAllSessions(Authentication auth) {
        revokeAll(auth.getName());
        return ResponseEntity.noContent().build();
    }

    // 비밀번호 변경 · "모든 기기 로그아웃"
    void revokeAll(String username) {
      sessions.findByPrincipalName(username)
          .keySet()
          .forEach(sessions::deleteById);
    }
    // sessions: 수업 화면은 Spring Session 의 FindByIndexNameSessionRepository (PrincipalSessionRepository 주석 참고)
}
