package board.user;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 4-1 프로필 수정: 요청 DTO 의 필드를 하나씩 옮긴다 (보낸 필드만 바꾼다). */
@Service
public class ProfileService {
    private final AppUserRepository users;

    public ProfileService(AppUserRepository users) {
        this.users = users;
    }

    @Transactional
    public ProfileView update(String username, ProfileUpdate u) {
        AppUser me = users.findByUsername(username).orElseThrow();
        if (u.nickname() != null) me.setNickname(u.nickname());
        if (u.bio() != null) me.setBio(u.bio());
        return ProfileView.of(me);
    }
}
