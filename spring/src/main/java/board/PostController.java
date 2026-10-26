package board;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** 게시판 CRUD + 검색 (안전한 구현). */
@RestController
@RequestMapping("/api/posts")
@Validated
public class PostController {

    public record PostRequest(
        @NotBlank @Size(max = 100) String title,
        @NotBlank @Size(max = 5000) String content) {}

    public record PostSummary(Long id, String title, String author) {
        static PostSummary of(Post p) {
            return new PostSummary(p.getId(), p.getTitle(), p.getAuthor());
        }
    }

    public record PostDetail(Long id, String title, String content, String author, String createdAt) {
        static PostDetail of(Post p) {
            return new PostDetail(p.getId(), p.getTitle(), p.getContent(), p.getAuthor(),
                p.getCreatedAt().toString());
        }
    }

    private final PostRepository posts;
    private final PostJdbcSearch jdbcSearch;

    public PostController(PostRepository posts, PostJdbcSearch jdbcSearch) {
        this.posts = posts;
        this.jdbcSearch = jdbcSearch;
    }

    /** GET /api/posts?title=공지 : 제목 일치 검색 (JPQL 파라미터 바인딩). */
    @GetMapping
    public List<PostSummary> search(@RequestParam(required = false) @Size(max = 100) String title) {
        if (title == null) {
            return posts.findAll().stream().map(PostSummary::of).toList();
        }
        return posts.findByTitle(title).stream().map(PostSummary::of).toList();
    }

    /** GET /api/posts/search?keyword=&sort=createdAt&dir=desc : JdbcTemplate 바인딩 + ORDER BY 허용 목록. */
    @GetMapping("/search")
    public List<Map<String, Object>> searchJdbc(
            @RequestParam(defaultValue = "") @Size(max = 100) String keyword,
            @RequestParam(defaultValue = "createdAt") @Pattern(regexp = "^[A-Za-z]{1,20}$") String sort,
            @RequestParam(defaultValue = "desc") String dir) {
        return jdbcSearch.search(keyword, sort, dir);
    }

    @GetMapping("/{id}")
    public PostDetail get(@PathVariable long id) {
        return PostDetail.of(find(id));
    }

    @PostMapping
    public ResponseEntity<PostDetail> create(@Valid @RequestBody PostRequest req, Authentication auth) {
        Post saved = posts.save(new Post(req.title(), req.content(), auth.getName()));
        return ResponseEntity.created(URI.create("/api/posts/" + saved.getId())).body(PostDetail.of(saved));
    }

    @PutMapping("/{id}")
    public PostDetail update(@PathVariable long id, @Valid @RequestBody PostRequest req, Authentication auth) {
        Post post = find(id);
        checkOwner(post, auth);
        post.update(req.title(), req.content());
        return PostDetail.of(posts.save(post));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id, Authentication auth) {
        Post post = find(id);
        checkOwner(post, auth);
        posts.delete(post);
        return ResponseEntity.noContent().build();
    }

    private Post find(long id) {
        return posts.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    // 작성자 본인 또는 관리자만 수정·삭제 (IDOR 방지)
    private static void checkOwner(Post post, Authentication auth) {
        boolean admin = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (!admin && !post.getAuthor().equals(auth.getName())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }
}
