/*
 * ================================================================
 *  의도적으로 취약한 실습 코드 — 운영에 쓰지 마세요
 *  lab-vulnerable 프로필 + server.address=127.0.0.1 에서만 켜집니다.
 *  안전한 구현은 같은 기능의 /api/** 쪽 클래스를 보세요.
 * ================================================================
 */
package board.lab;

import board.PostRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

/** 4-2 저장형 XSS: th:utext 로 저장된 글을 그대로 출력한다. 안전한 쪽은 board.PostViewController */
@Controller
@Profile("lab-vulnerable")
public class LabPostViewController {
    private final PostRepository posts;

    public LabPostViewController(PostRepository posts) {
        this.posts = posts;
    }

    @GetMapping("/lab/posts/{id}/view")
    String view(@PathVariable long id, Model model) {
        model.addAttribute("post", posts.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)));
        return "lab/post";
    }
}
