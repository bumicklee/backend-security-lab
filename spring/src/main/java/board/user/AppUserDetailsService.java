package board.user;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsPasswordService;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 3-1: UserDetailsPasswordService 를 함께 구현하면, 로그인에 성공했는데 저장된 해시가
 * 기본 알고리즘({argon2})이 아닐 때 Spring Security 가 updatePassword 를 호출한다.
 */
@Service public class AppUserDetailsService
    implements UserDetailsService, UserDetailsPasswordService {
  private final AppUserRepository users;

  public AppUserDetailsService(AppUserRepository users) {
    this.users = users;
  }

  @Override
  public UserDetails loadUserByUsername(String username) {
    AppUser u = users.findByUsername(username)
        .orElseThrow(() -> new UsernameNotFoundException("not found"));
    return User.withUsername(u.getUsername())
        .password(u.getPasswordHash())
        .roles(u.getRole())
        .build();
  }

  // 로그인 성공 + 저장된 해시가 {argon2}가 아니면 Spring Security가 호출한다
  @Override @Transactional
  public UserDetails updatePassword(UserDetails user, String newHash) {
    users.findByUsername(user.getUsername())
        .ifPresent(u -> u.changePasswordHash(newHash));   // {bcrypt}… → {argon2}…
    return User.withUserDetails(user).password(newHash).build();
  }
}
