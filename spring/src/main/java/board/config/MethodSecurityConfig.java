package board.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/** 3-4: @PreAuthorize·@PostAuthorize 를 쓰기 위한 메서드 보안 (설정 클래스에 한 번) */
@Configuration
@EnableMethodSecurity
class MethodSecurityConfig {}
