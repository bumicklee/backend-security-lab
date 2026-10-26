package board;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/** lab-vulnerable 프로필은 127.0.0.1 바인딩이 아니면 기동하지 않는다 */
class LabProfileGuardTest {

    @Test
    @DisplayName("lab-vulnerable + server.address=0.0.0.0 이면 기동 실패")
    void refusesNonLoopbackBinding() {
        assertThatThrownBy(() -> {
            ConfigurableApplicationContext ctx = new SpringApplicationBuilder(BoardApplication.class)
                .profiles("test", "lab-vulnerable")
                // 명령행 인자 = 환경 변수(SERVER_ADDRESS)처럼 yml 보다 우선한다
                .run("--server.port=0", "--server.address=0.0.0.0");
            ctx.close();   // 여기까지 오면 안 된다
        }).hasStackTraceContaining("server.address=127.0.0.1");
    }

    @Test
    @DisplayName("lab-vulnerable + 127.0.0.1 이면 기동하고 /lab 컨트롤러가 등록된다")
    void startsOnLoopback() {
        try (ConfigurableApplicationContext ctx = new SpringApplicationBuilder(BoardApplication.class)
                .profiles("test", "lab-vulnerable")
                .run("--server.port=0")) {
            assertThat(ctx.getEnvironment().getProperty("server.address")).isEqualTo("127.0.0.1");
            assertThat(ctx.containsBean("labPostController")).isTrue();
        }
    }

    @Test
    @DisplayName("기본 프로필에는 /lab 컨트롤러가 없다")
    void defaultProfileHasNoLabBeans() {
        try (ConfigurableApplicationContext ctx = new SpringApplicationBuilder(BoardApplication.class)
                .profiles("test")
                .run("--server.port=0")) {
            assertThat(ctx.containsBean("labPostController")).isFalse();
            assertThat(ctx.containsBean("labSafetyGuard")).isFalse();
        }
    }
}
