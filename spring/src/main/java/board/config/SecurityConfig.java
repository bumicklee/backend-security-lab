package board.config;

import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.password.CompromisedPasswordChecker;
import org.springframework.security.authentication.password.CompromisedPasswordDecision;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.authorization.EnableMultiFactorAuthentication;
import org.springframework.security.config.annotation.authorization.MultiFactorCondition;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.password.HaveIBeenPwnedRestApiPasswordChecker;
import org.springframework.security.web.webauthn.management.MapPublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.MapUserCredentialRepository;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Spring Security 설정. CSRF 보호·보안 헤더·세션 고정 방어는 기본값 그대로 켜 둔다 (0-5).
 *
 * <p>4-2 CSP 는 기본 헤더에 없어 직접 추가한다. 4-5 CSRF 는 끄지 않고 SPA 용 설정({@code spa()}),
 * CORS 는 허용할 출처만 + 자격 증명.
 *
 * <p>3-6 MFA: 비밀번호 + 패스키 두 요소를 요구한다. 수업 화면은 모든 사용자에게 요구하지만,
 * 이 저장소는 {@code when = WEBAUTHN_REGISTERED} 를 붙여 <b>패스키를 등록한 사용자에게만</b> 요구한다
 * (패스키가 없는 실습 계정·기존 테스트가 그대로 동작하도록).
 */
@Configuration
@EnableMultiFactorAuthentication(
    authorities = { FactorGrantedAuthority.PASSWORD_AUTHORITY, FactorGrantedAuthority.WEBAUTHN_AUTHORITY },
    when = MultiFactorCondition.WEBAUTHN_REGISTERED)
public class SecurityConfig {

    @Bean
    SecurityFilterChain chain(HttpSecurity http, Environment env,
            ObjectProvider<ClientRegistrationRepository> oauthClients,
            @Value("${app.webauthn.rp-id}") String rpId,
            @Value("${app.webauthn.allowed-origins}") String[] allowedOrigins) throws Exception {
        boolean lab = Arrays.asList(env.getActiveProfiles()).contains("lab-vulnerable");

        http
            .authorizeHttpRequests(a -> a
                .requestMatchers("/health", "/error").permitAll()
                .requestMatchers("/api/csrf", "/api/auth/login", "/api/auth/signup").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/posts", "/api/posts/**").permitAll()
                .requestMatchers(HttpMethod.HEAD, "/api/posts", "/api/posts/**").permitAll() // curl -I (4-2)
                .requestMatchers(HttpMethod.GET, "/api/files", "/api/avatars/*", "/api/preview").permitAll()
                // 웹훅은 세션이 아니라 HMAC 서명으로 인증한다 (WebhookVerifier)
                .requestMatchers(HttpMethod.POST, "/api/webhooks/**").permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                // 취약 실습 경로는 lab-vulnerable 프로필에서만 열린다
                .requestMatchers("/lab/**").access((auth, ctx) ->
                    new org.springframework.security.authorization.AuthorizationDecision(lab))
                .anyRequest().authenticated())
            // 4-5 기본으로 켜진 CSRF 방어를 끄지 않는다. SPA 용: 쿠키 저장소 + BREACH 대비 토큰 처리
            // XSRF-TOKEN 쿠키 → X-XSRF-TOKEN 헤더 (웹훅은 HMAC 서명, /lab 은 실습용이라 제외)
            .csrf(c -> c.spa()
                .ignoringRequestMatchers("/api/webhooks/**", "/lab/**"))
            // 4-5 CORS: 아래 corsConfigurationSource 빈 (허용 출처만 + 쿠키)
            .cors(Customizer.withDefaults())
            // 4-2 CSP 는 기본 헤더에 없다 → 직접 추가. 인라인 스크립트는 .js 파일로 옮긴다
            .headers(h -> h
                .contentSecurityPolicy(csp -> csp
                    .policyDirectives("default-src 'self';"
                        + " script-src 'self';"
                        + " object-src 'none';"
                        + " base-uri 'none';"
                        + " frame-ancestors 'none'")))
            .exceptionHandling(e -> e
                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                // 3-6: 비밀번호만 통과했고 패스키 요소가 없으면 401 + webauthn_required
                .defaultDeniedHandlerForMissingAuthority(webAuthnRequired(), FactorGrantedAuthority.WEBAUTHN_AUTHORITY))
            .formLogin(f -> f.disable())
            .httpBasic(b -> b.disable())
            // 3-6 패스키: 이 도메인 전용 키, 다른 오리진에서 만든 서명은 거절
            .webAuthn(w -> w.rpName("Board").rpId(rpId)
                .allowedOrigins(allowedOrigins)
                .disableDefaultRegistrationPage(true));

        // 3-3 OAuth 2.0·OIDC 로그인: IdP 등록(spring.security.oauth2.client.*)이 있을 때만 켠다.
        // PKCE·state·nonce·ID 토큰 검증은 Spring Security 가 자동으로 한다.
        if (oauthClients.getIfAvailable() != null) {
            http.oauth2Login(Customizer.withDefaults());
        }
        return http.build();
    }

    /**
     * 4-5 쿠키(세션)를 쓰는 CORS: 허용할 출처만 정확히. "*" 패턴·요청 Origin 반사는 금지.
     * 허용 목록 밖 Origin 에는 Access-Control-Allow-Origin 을 주지 않는다 (CORS 는 CSRF 방어가 아니다).
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins}") String[] allowedOrigins) {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(List.of(allowedOrigins));
        cfg.setAllowCredentials(true);
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE"));
        cfg.setAllowedHeaders(List.of("Content-Type", "X-XSRF-TOKEN", "Idempotency-Key"));
        cfg.setMaxAge(600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }

    private static AuthenticationEntryPoint webAuthnRequired() {
        return (request, response, ex) -> {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"webauthn_required\"}");
        };
    }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * 3-1: 새 비밀번호는 Argon2id(OWASP 최소값: 메모리 19 MiB, 반복 2, 병렬 1)로,
     * 해시 앞 {argon2}·{bcrypt} 접두어로 알고리즘을 구분해 예전 bcrypt 해시도 검증한다.
     * 저장 예: {argon2}$argon2id$v=19$m=19456,t=2,p=1$...
     */
    @Bean
    PasswordEncoder passwordEncoder() {
      Map<String, PasswordEncoder> m = Map.of(
        "argon2", new Argon2PasswordEncoder(
            16, 32, 1, 19456, 2),
        "bcrypt", new BCryptPasswordEncoder());
      return new DelegatingPasswordEncoder(
          "argon2", m);
    }

    /**
     * 3-5: 로그인 때 비밀번호가 유출 목록에 있는지 확인한다 (해시 앞 5자리만 보낸다, k-익명성).
     * DaoAuthenticationProvider 가 이 빈을 찾아 비밀번호가 맞은 뒤에 검사한다.
     * 테스트·오프라인 실습에서는 app.security.compromised-password-check=false 로 끈다.
     */
    @Bean
    CompromisedPasswordChecker compromisedPasswordChecker(
            @Value("${app.security.compromised-password-check:true}") boolean enabled) {
        if (!enabled) {
            return password -> new CompromisedPasswordDecision(false);
        }
        return new HaveIBeenPwnedRestApiPasswordChecker();
    }

    /** 3-6: 패스키 사용자·자격 증명 저장소 (실습용 메모리. 운영은 JdbcUserCredentialRepository 등) */
    @Bean
    PublicKeyCredentialUserEntityRepository userEntityRepository() {
        return new MapPublicKeyCredentialUserEntityRepository();
    }

    @Bean
    UserCredentialRepository userCredentialRepository() {
        return new MapUserCredentialRepository();
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
