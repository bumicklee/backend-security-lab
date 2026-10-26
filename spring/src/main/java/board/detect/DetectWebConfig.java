package board.detect;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 6-2 조회량 감시는 개인정보를 돌려주는 경로(주소 조회)에만 건다. */
@Configuration
public class DetectWebConfig implements WebMvcConfigurer {
    private final BulkLookupGuard guard;

    public DetectWebConfig(BulkLookupGuard guard) {
        this.guard = guard;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(guard).addPathPatterns("/api/addresses", "/api/addresses/**");
    }
}
