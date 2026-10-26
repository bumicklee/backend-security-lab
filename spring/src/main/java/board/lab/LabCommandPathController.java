/*
 * ================================================================
 *  의도적으로 취약한 실습 코드 — 운영에 쓰지 마세요
 *  lab-vulnerable 프로필 + server.address=127.0.0.1 에서만 켜집니다.
 *  안전한 구현은 같은 기능의 /api/** 쪽 클래스를 보세요.
 * ================================================================
 */
package board.lab;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 2-4 커맨드 인젝션·경로 조작. 안전한 쪽은 board.tools.PingController / FileController */
@RestController
@Profile("lab-vulnerable")
public class LabCommandPathController {
    private final String baseDir;

    public LabCommandPathController(@Value("${app.files.dir}") String baseDir) {
        this.baseDir = baseDir;
    }

    // ✗ 셸(sh -c)에 문자열을 통째로 넘긴다 → "; id" 가 별도 명령으로 실행된다
    @GetMapping(value = "/lab/admin/ping", produces = "text/plain;charset=UTF-8")
    String ping(@RequestParam String host)
        throws Exception {
      // 파이프를 쓰려고 셸을 불렀다
      Process p = new ProcessBuilder(
          "sh", "-c", "ping -c 1 " + host)
          .redirectErrorStream(true).start();
      p.waitFor(10, TimeUnit.SECONDS);   // 실습 PC 보호용 시간 제한
      return new String(
          p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }

    // ✗ 입력을 경로 문자열에 그대로 붙인다 → ../../etc/passwd
    @GetMapping("/lab/files")
    Resource file(@RequestParam String name) {
      return new FileSystemResource(
          baseDir + "/" + name);
    }
}
