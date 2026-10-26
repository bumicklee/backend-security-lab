/*
 * ================================================================
 *  의도적으로 취약한 실습 코드 — 운영에 쓰지 마세요
 *  lab-vulnerable 프로필 + server.address=127.0.0.1 에서만 켜집니다.
 *  안전한 구현은 같은 기능의 /api/** 쪽 클래스를 보세요.
 * ================================================================
 */
package board.lab;

import board.Post;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 2-3 SQL 인젝션: 문자열 결합 쿼리. 안전한 쪽은 board.PostRepository / board.PostJdbcSearch */
@RestController
@Profile("lab-vulnerable")
public class LabPostController {
    private final JdbcTemplate jdbc;
    private final EntityManager em;

    public LabPostController(JdbcTemplate jdbc, EntityManager em) {
        this.jdbc = jdbc;
        this.em = em;
    }

    // ✗ JdbcTemplate: 문자열로 쿼리 조립
    @GetMapping("/lab/posts")
    public List<Map<String, Object>> search(@RequestParam String title) {
      String sql = "SELECT id, title, author FROM posts WHERE title = '" + title + "'";
      return jdbc.queryForList(sql);
    }

    // ✗ JPA 라도 JPQL 을 이어 붙이면 같다
    @GetMapping("/lab/posts/jpql")
    public List<Map<String, Object>> searchJpql(@RequestParam String title) {
        List<Post> found = em.createQuery("SELECT p FROM Post p"
            + " WHERE p.title = '" + title + "'",
            Post.class).getResultList();
        return found.stream()
            .map(p -> Map.<String, Object>of("id", p.getId(), "title", p.getTitle(), "author", p.getAuthor()))
            .toList();
    }

    // ✗ ORDER BY 동적 컬럼을 그대로 이어 붙인다 (허용 목록 없음)
    @GetMapping("/lab/posts/search")
    public List<Map<String, Object>> sorted(@RequestParam(defaultValue = "id") String sort) {
        return jdbc.queryForList("SELECT id, title, author FROM posts ORDER BY " + sort);
    }
}
