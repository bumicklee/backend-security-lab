package board;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 강의 예제 서비스: 게시판 + 파일 업로드 + 결제 웹훅 + 앱 연동 API.
 * 안전한 구현은 /api/**, 의도적으로 취약한 실습 코드는 /lab/** (lab-vulnerable 프로필 전용).
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling   // 5-4 파기 배치 (privacy.RetentionJob)
public class BoardApplication {
    public static void main(String[] args) {
        SpringApplication.run(BoardApplication.class, args);
    }
}
