// semgrep --test 용 예시. 실제 앱 코드가 아니다 (.semgrepignore 로 스캔 제외)
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RestController;

class Examples {
    // ruleid: board-no-cors-any-origin
    @CrossOrigin(origins = "*")
    @RestController
    class AnyOrigin {}

    // ruleid: board-no-cors-any-origin
    @CrossOrigin
    @RestController
    class NoArgs {}

    // ruleid: board-no-cors-any-origin
    @CrossOrigin()
    @RestController
    class EmptyParens {}

    // ok: board-no-cors-any-origin
    @CrossOrigin(origins = "https://app.example.com")
    @RestController
    class AllowList {}
}
