package board.lab;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

/**
 * lab-vulnerable 프로필 안전장치.
 * 취약한 /lab/** 엔드포인트가 켜질 때 server.address 가 127.0.0.1 이 아니면 기동을 멈춘다.
 * 웹 서버가 포트를 열기 전에 빈 초기화 단계에서 예외를 던지므로 외부에 노출될 틈이 없다.
 */
@Configuration
@Profile("lab-vulnerable")
public class LabSafetyGuard implements InitializingBean {
    private static final Logger log = LoggerFactory.getLogger(LabSafetyGuard.class);
    static final String REQUIRED_ADDRESS = "127.0.0.1";

    private final Environment env;

    public LabSafetyGuard(Environment env) {
        this.env = env;
    }

    @Override
    public void afterPropertiesSet() {
        check(env.getProperty("server.address"));
        log.warn("!!! lab-vulnerable 프로필: 의도적으로 취약한 /lab/** 엔드포인트가 켜졌습니다. "
            + "127.0.0.1 에서만 접속 가능합니다. 운영·공유 서버에서 쓰지 마세요. !!!");
    }

    static void check(String address) {
        if (!REQUIRED_ADDRESS.equals(address)) {
            throw new IllegalStateException(
                "lab-vulnerable 프로필은 server.address=127.0.0.1 에서만 기동합니다 (현재: " + address + "). "
                    + "취약한 실습 코드를 네트워크에 노출하지 마세요.");
        }
    }
}
