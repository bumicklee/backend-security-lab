package board.user;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

/**
 * 5-4 최소 수집: 게시판 가입에 필요한 것만 받는다.
 * 모르는 필드(residentNo · address …)는 Jackson 이 400 으로 거절한다 (fail-on-unknown-properties).
 */
@RestController
@RequestMapping("/api/auth")
public class SignupController {

    public record SignupRequest(
        @Email @NotBlank @Size(max = 50) String email,
        @NotNull @Size(min = 12, max = 200) String password,
        @NotBlank @Size(max = 30) String nickname,
        @AssertTrue boolean over14) {}
    // 주소는 첫 배송 때 받는다
    // 주민등록번호: 법령 근거 없으면 금지

    private final AppUserRepository users;
    private final PasswordEncoder encoder;

    public SignupController(AppUserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    @PostMapping("/signup")
    ResponseEntity<Map<String, String>> signup(@Valid @RequestBody SignupRequest req) {
        String username = req.email().toLowerCase();
        if (users.findByUsername(username).isPresent()) return ResponseEntity.status(HttpStatus.CONFLICT).build();
        AppUser u = new AppUser(username, username, encoder.encode(req.password()), "USER");
        u.setNickname(req.nickname());
        users.save(u);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("username", username, "nickname", req.nickname()));
    }
}
