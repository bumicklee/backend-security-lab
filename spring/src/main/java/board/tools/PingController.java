package board.tools;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 관리자용 네트워크 점검 API (2-4 커맨드 인젝션).
 * 셸을 거치지 않고 인자 배열로 실행한다. 관리자(ROLE_ADMIN)만 호출 가능.
 */
@RestController
public class PingController {

    static final Pattern HOST =
        Pattern.compile("^[A-Za-z0-9.-]{1,253}$");

    @GetMapping(value = "/api/admin/ping", produces = "text/plain;charset=UTF-8")
    String ping(@RequestParam String host) throws IOException, InterruptedException {
        // 허용 목록 형식만 통과: 공백·; | $ ` 같은 메타문자는 여기서 끝
        if (!HOST.matcher(host).matches())
          throw new ResponseStatusException(
              HttpStatus.BAD_REQUEST);
        // sh -c 없이 인자 배열로, -- 로 옵션 주입(-f 등)도 막는다
        Process p = new ProcessBuilder(
            "ping", "-c", "1", "--", host)
            .redirectErrorStream(true).start();
        if (!p.waitFor(5, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT);
        }
        return new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }
}
