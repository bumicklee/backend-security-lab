package board.config;

import board.Post;
import board.member.MemberRepository;
import board.member.MemberService;
import board.PostRepository;
import board.order.Order;
import board.order.OrderRepository;
import board.user.AppUser;
import board.user.AppUserRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** 실습용 가짜 데이터. 이메일·비밀번호 모두 예시 값이다. */
@Component
public class DataSeeder implements ApplicationRunner {
    private final AppUserRepository users;
    private final PostRepository posts;
    private final OrderRepository orders;
    private final PasswordEncoder encoder;
    private final String adminPassword;
    private final String alicePassword;
    private final String bobPassword;
    private final Path filesDir;
    private final MemberRepository members;
    private final MemberService memberService;
    private final JdbcTemplate jdbc;

    public DataSeeder(AppUserRepository users, PostRepository posts, OrderRepository orders, PasswordEncoder encoder,
            @Value("${app.seed.admin-password}") String adminPassword,
            @Value("${app.seed.alice-password}") String alicePassword,
            @Value("${app.seed.bob-password}") String bobPassword,
            @Value("${app.files.dir}") String filesDir,
            MemberRepository members, MemberService memberService, JdbcTemplate jdbc) {
        this.users = users;
        this.posts = posts;
        this.orders = orders;
        this.encoder = encoder;
        this.adminPassword = adminPassword;
        this.alicePassword = alicePassword;
        this.bobPassword = bobPassword;
        this.filesDir = Path.of(filesDir);
        this.members = members;
        this.memberService = memberService;
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        if (users.count() == 0) {
            users.save(new AppUser("admin", "admin@example.com", encoder.encode(adminPassword), "ADMIN"));
            users.save(new AppUser("alice", "alice@example.com", encoder.encode(alicePassword), "USER"));
        }
        if (users.findByUsername("bob").isEmpty()) {
            users.save(new AppUser("bob", "bob@example.com", encoder.encode(bobPassword), "USER"));
        }
        // 4-1 프로필 필드 + 내부 필드(재설정 토큰, 가짜 값)
        for (AppUser u : users.findAll()) {
            if (u.getResetToken() == null) {
                if (u.getNickname() == null) u.setNickname(u.getUsername());
                u.issueResetToken("rt_EXAMPLE_" + u.getUsername() + "_0000");
                users.save(u);
            }
        }
        if (orders.count() == 0) {
            // 3-4·3-7: 주문 1000~1010. 1001 은 alice(24,000원), 1002 는 bob(30,000원)
            String[] owners = {"admin", "alice", "bob", "alice", "bob", "alice", "bob", "alice", "bob", "alice", "bob"};
            long[][] items = {{2, 1}, {1, 2}, {7, 1}, {2, 3}, {1, 1}, {7, 2}, {2, 2}, {1, 3}, {2, 1}, {7, 1}, {1, 4}};
            for (int i = 0; i < owners.length; i++) {
                long productId = items[i][0];
                int qty = (int) items[i][1];
                long price = productId == 1 ? 12_000 : productId == 2 ? 5_000 : 30_000;
                orders.save(new Order(1000 + i, owners[i], productId, qty, price * qty, null));
            }
        }
        if (posts.count() == 0) {
            posts.save(new Post("공지", "실습 서버는 인터넷에 열지 마세요.", "admin"));
            posts.save(new Post("가입 인사", "안녕하세요, alice 입니다.", "alice"));
            posts.save(new Post("질문", "파일 업로드는 몇 MB까지 되나요?", "alice"));
        }
        // 4-2 XSS 예시 글: Express 와 같은 id 77 고정 (/api/posts/77/view 는 이스케이프, /lab/posts/77/view 는 그대로).
        // 자동 번호(IDENTITY)는 건드리지 않으므로 JPA 가 아니라 id 를 직접 넣는다
        if (!posts.existsById(77L)) {
            jdbc.update("INSERT INTO posts (id, title, content, author, created_at) VALUES (77, ?, ?, ?, ?)",
                "이벤트 안내", "<img src=x onerror=alert(document.cookie)>", "bob", Timestamp.from(Instant.now()));
        }
        // 5-2·5-4·5-5 회원(가짜 연락처). 전화번호는 봉투 암호화 + 블라인드 인덱스로 저장된다.
        // carol 은 400일 전에 탈퇴 → 파기 배치(RetentionJob.purgeExpired)가 지운다
        if (members.count() == 0) {
            memberService.create("alice", "alice@example.com", "010-2345-6789");
            memberService.create("bob", "bob@example.com", "010-3456-7890");
            var carol = memberService.create("carol", "carol@example.com", "010-4567-8901");
            carol.withdraw(Instant.now().minus(Duration.ofDays(400)));
            members.save(carol);
        }
        // GET /api/files?name=hello.txt 로 내려받을 예시 파일
        Files.createDirectories(filesDir);
        Path hello = filesDir.resolve("hello.txt");
        if (Files.notExists(hello)) {
            Files.writeString(hello, "hello from /srv/files (example)\n");
        }
    }
}
