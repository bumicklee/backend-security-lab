package board;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostRepository extends JpaRepository<Post, Long> {

    // JPA: 이름 있는 파라미터. 입력은 값으로만 바인딩된다 (2-3)
    @Query("SELECT p FROM Post p"
         + " WHERE p.title = :title")
    List<Post> findByTitle(
        @Param("title") String title);
}
