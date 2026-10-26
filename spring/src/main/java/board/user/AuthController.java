package board.user;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.password.CompromisedPasswordException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

/**
 * 로그인은 서버 세션(JSESSIONID, HttpOnly 쿠키)으로 발급한다.
 * 세션 고정 방어: 로그인 성공 시 세션 ID를 바꾼다.
 * 3-5: 계정별 실패 누적(LoginAttempts) → 429 + Retry-After, 유출된 비밀번호 → 403 password_reset_required.
 */
@RestController
@RequestMapping("/api")
public class AuthController {

    // 문자열만 받는다. 객체·배열이 오면 400 (2-3의 연산자 주입과 같은 원리)
    public record LoginRequest(
        @NotBlank @Size(max = 50) String username,
        @NotBlank @Size(max = 200) String password) {}

    private final AuthenticationManager authManager;
    private final LoginAttempts attempts;
    private final PrincipalSessionRepository sessions;
    private final SecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();
    private final ChangeSessionIdAuthenticationStrategy sessionFixation = new ChangeSessionIdAuthenticationStrategy();

    public AuthController(AuthenticationManager authManager, LoginAttempts attempts,
            PrincipalSessionRepository sessions) {
        this.authManager = authManager;
        this.attempts = attempts;
        this.sessions = sessions;
    }

    /**
     * 프런트·curl 이 CSRF 토큰을 받아 가는 주소. 쿠키 XSRF-TOKEN 도 함께 내려간다.
     * 4-5 csrf.spa(): 헤더(X-XSRF-TOKEN)에는 XSRF-TOKEN 쿠키 값을 그대로 보낸다.
     * 이 응답의 token 은 BREACH 대비로 요청마다 다르게 가린 값이라 폼 파라미터(parameterName)로 보낸다.
     */
    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("headerName", token.getHeaderName(), "parameterName", token.getParameterName(),
            "token", token.getToken());
    }

    @PostMapping("/auth/login")
    public ResponseEntity<Map<String, String>> login(@Valid @RequestBody LoginRequest req,
            HttpServletRequest request, HttpServletResponse response) {
        String u = req.username(), p = req.password();
        // 계정 기준 대기 (계정이 있는지와 상관없이 같은 규칙)
        long wait = attempts.retryAfterSec(u);
        if (wait > 0) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(wait))
                .body(Map.of("error", "too_many_attempts"));
        }
        Authentication auth;
        try {
          auth = authManager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(u, p));
        } catch (CompromisedPasswordException e) {               // AuthenticationException보다 먼저
          return ResponseEntity.status(403).body(Map.of("error", "password_reset_required"));
        } catch (AuthenticationException e) {
          attempts.fail(u);
          return invalidCredentials();
        }
        attempts.reset(u);
        if (request.getSession(false) != null) {
            sessionFixation.onAuthentication(auth, request, response);
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);
        // 3-6: "모든 기기 로그아웃"을 위해 사용자별 세션 색인에 등록
        sessions.register(auth.getName(), request.getSession());
        return ResponseEntity.ok(Map.of("user", auth.getName(), "role", roleOf(auth)));
    }

    // 아이디가 없는지 비밀번호가 틀렸는지 구분하지 않는다
    private static ResponseEntity<Map<String, String>> invalidCredentials() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "invalid_credentials"));
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response, Authentication auth) {
        new SecurityContextLogoutHandler().logout(request, response, auth);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public Map<String, String> me(Authentication auth) {
        return Map.of("user", auth.getName(), "role", roleOf(auth));
    }

    private static String roleOf(Authentication auth) {
        return auth.getAuthorities().stream()
            .map(a -> a.getAuthority())
            .filter(a -> a.startsWith("ROLE_"))       // FACTOR_PASSWORD 같은 인증 요소 권한은 건너뛴다
            .map(a -> a.replace("ROLE_", ""))
            .findFirst().orElse("USER");
    }
}
