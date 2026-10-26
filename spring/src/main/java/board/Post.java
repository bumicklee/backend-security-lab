package board;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "posts")
public class Post {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, length = 5000)
    private String content;

    // 작성자는 요청 본문이 아니라 로그인 정보에서 채운다 (mass assignment 방지)
    @Column(nullable = false, length = 50)
    private String author;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Post() {}

    public Post(String title, String content, String author) {
        this.title = title;
        this.content = content;
        this.author = author;
    }

    public void update(String title, String content) {
        this.title = title;
        this.content = content;
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public String getAuthor() { return author; }
    public Instant getCreatedAt() { return createdAt; }
}
