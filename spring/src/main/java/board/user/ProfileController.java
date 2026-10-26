package board.user;

import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 4-1 내 프로필 수정. 요청은 ProfileUpdate, 응답은 ProfileView — 엔티티를 그대로 받거나 내보내지 않는다.
 * 취약한 쪽은 board.lab.LabProfileController
 */
@RestController
public class ProfileController {
    private final ProfileService profiles;

    public ProfileController(ProfileService profiles) {
        this.profiles = profiles;
    }

    @PatchMapping("/api/users/me")
    ProfileView update(Authentication auth,
        @Valid @RequestBody ProfileUpdate u) {
      return profiles.update(auth.getName(), u);
    }
}
