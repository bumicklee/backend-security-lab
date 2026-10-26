package board;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;

/**
 * JdbcTemplate 검색 (2-3).
 * 값은 ? 자리에 바인딩하고, 바인딩할 수 없는 ORDER BY 컬럼은 허용 목록에서 고른다.
 */
@Repository
public class PostJdbcSearch {

    // 정렬 컬럼 허용 목록: 요청 값 → 실제 컬럼 이름
    static final Map<String, String> SORT_COLUMNS = Map.of(
        "id", "id",
        "title", "title",
        "createdAt", "created_at");

    private final JdbcTemplate jdbc;

    public PostJdbcSearch(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> search(String keyword, String sort, String dir) {
        String column = SORT_COLUMNS.get(sort);
        if (column == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sort");
        }
        String direction = "asc".equalsIgnoreCase(dir) ? "ASC" : "DESC";
        // 컬럼·방향은 코드가 고른 상수만 이어 붙이고, 사용자 값은 ? 로
        return jdbc.queryForList(
            "SELECT id, title, author, created_at FROM posts"
                + " WHERE title LIKE ? ORDER BY " + column + " " + direction,
            "%" + escapeLike(keyword) + "%");
    }

    private static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
