package board;

import org.owasp.html.PolicyFactory;
import org.owasp.html.Sanitizers;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

/**
 * 4-2 게시글 보기 화면 (Thymeleaf). 출력은 th:text 의 자동 이스케이프로.
 * CSP·nosniff 헤더는 SecurityConfig 의 headers() 가 붙인다. 취약한 쪽은 board.lab.LabPostViewController
 */
@Controller
public class PostViewController {

    // 서식이 필요한 본문: 허용한 태그(굵게·기울임·링크·문단 등)만 남긴다
    static final PolicyFactory POLICY =
        Sanitizers.FORMATTING
            .and(Sanitizers.LINKS)
            .and(Sanitizers.BLOCKS);

    private final PostRepository posts;

    public PostViewController(PostRepository posts) {
        this.posts = posts;
    }

    @GetMapping("/api/posts/{id}/view")
    String view(@PathVariable long id, Model model) {
        model.addAttribute("post", find(id));
        return "post";                       // templates/post.html: th:text
    }

    @GetMapping("/api/posts/{id}/rich")
    String rich(@PathVariable long id, Model model) {
        Post post = find(id);
        model.addAttribute("post", post);
        // 출력 직전에 허용 태그만 남긴다
        model.addAttribute("bodyHtml",
            POLICY.sanitize(post.getContent()));
        return "post-rich";                  // 뷰: <div th:utext="${bodyHtml}">
    }

    public static String sanitize(String html) {
        return POLICY.sanitize(html);
    }

    private Post find(long id) {
        return posts.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
