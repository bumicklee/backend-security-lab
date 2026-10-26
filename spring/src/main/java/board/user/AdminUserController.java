package board.user;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 3-4 기능 수준 인가(BFLA): 관리자 기능은 서버에서 역할을 확인한다.
 * URL 규칙(/api/admin/** → ADMIN)에 더해 메서드에도 @PreAuthorize 를 붙였다.
 */
@RestController
@RequestMapping("/api")
public class AdminUserController {
    private final AppUserRepository users;

    public AdminUserController(AppUserRepository users) {
        this.users = users;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin/users")
    List<Map<String, String>> list() {
        // 비밀번호 해시 같은 내부 필드는 내보내지 않는다
        return users.findAll().stream()
            .map(u -> Map.of("username", u.getUsername(), "email", u.getEmail(), "role", u.getRole()))
            .toList();
    }

    @PreAuthorize("hasRole('ADMIN')")      // 기능 수준: 이 기능을 쓸 역할인가
    @DeleteMapping("/admin/users/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteUser(@PathVariable long id) { users.deleteById(id); }
}
