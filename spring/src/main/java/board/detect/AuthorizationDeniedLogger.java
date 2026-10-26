package board.detect;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authorization.AuthorizationEventPublisher;
import org.springframework.security.authorization.SpringAuthorizationEventPublisher;
import org.springframework.security.authorization.event.AuthorizationDeniedEvent;
import org.springframework.security.core.Authentication;

/**
 * 6-2 로그인한 사용자의 인가 실패(403)를 보안 이벤트(authz_denied)로 남긴다 → fail2ban(board-security)이 IP 별로 센다.
 * 로그인하지 않은 요청(401)은 남기지 않는다(그건 3-5 로그인 실패 쪽에서 다룬다).
 */
@Configuration
public class AuthorizationDeniedLogger {
    private final SecurityEvents events;

    public AuthorizationDeniedLogger(SecurityEvents events) {
        this.events = events;
    }

    @Bean
    AuthorizationEventPublisher authorizationEventPublisher(ApplicationEventPublisher publisher) {
        return new SpringAuthorizationEventPublisher(publisher);
    }

    @EventListener
    public void onDenied(AuthorizationDeniedEvent<?> e) {
        Authentication auth = e.getAuthentication().get();
        if (auth == null || auth instanceof AnonymousAuthenticationToken || !auth.isAuthenticated()) return;
        if (e.getSource() instanceof HttpServletRequest req) {
            events.publish("authz_denied", auth.getName(), req.getRemoteAddr());
        }
    }
}
