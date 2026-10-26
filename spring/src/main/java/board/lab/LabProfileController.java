/*
 * ================================================================
 *  의도적으로 취약한 실습 코드 — 운영에 쓰지 마세요
 *  lab-vulnerable 프로필 + server.address=127.0.0.1 에서만 켜집니다.
 *  안전한 구현은 같은 기능의 /api/** 쪽 클래스를 보세요.
 * ================================================================
 */
package board.lab;

import board.user.AppUser;
import board.user.AppUserRepository;
import org.springframework.beans.BeanUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 4-1 mass assignment + 과도한 데이터 노출: 요청 본문을 엔티티로 받아 속성을 통째로 복사하고,
 * 엔티티를 그대로 응답한다 (passwordHash·resetToken 까지 나간다).
 * 안전한 쪽은 board.user.ProfileController (ProfileUpdate · ProfileView)
 */
@RestController
@Profile("lab-vulnerable")
public class LabProfileController {
    private final AppUserRepository users;

    public LabProfileController(AppUserRepository users) {
        this.users = users;
    }

    @PatchMapping("/lab/users/me")
    @Transactional
    AppUser update(@RequestBody AppUser body,
        Authentication auth) {
      AppUser me = users.findByUsername(
          auth.getName()).orElseThrow();
      BeanUtils.copyProperties(body, me, "id");
      return users.save(me); // 엔티티 그대로
    }
}
