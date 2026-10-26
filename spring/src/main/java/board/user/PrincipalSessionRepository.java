package board.user;

import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 3-6: "사용자 이름 → 그 사용자의 세션들" 색인.
 *
 * <p>수업 화면은 Spring Session(JDBC·Redis)의 {@code FindByIndexNameSessionRepository} 를 쓴다.
 * 이 저장소는 H2 테스트와 단일 서버 실습을 단순하게 하려고 같은 이름의 메서드
 * ({@code findByPrincipalName}, {@code deleteById})를 가진 메모리 구현을 쓴다.
 * 서버가 여러 대라면 Spring Session 으로 바꿔야 모든 서버의 세션을 끊을 수 있다.
 */
@Component
public class PrincipalSessionRepository implements HttpSessionListener {
    private final Map<String, Map<String, HttpSession>> byPrincipal = new ConcurrentHashMap<>();
    private final Map<String, String> principalOf = new ConcurrentHashMap<>();

    /** 로그인 성공(세션 ID 교체 뒤)에 호출한다 */
    public void register(String username, HttpSession session) {
        byPrincipal.computeIfAbsent(username, k -> new ConcurrentHashMap<>()).put(session.getId(), session);
        principalOf.put(session.getId(), username);
    }

    /** 세션 ID → 세션. 호출하는 쪽이 지우면서 돌 수 있도록 복사본을 준다 */
    public Map<String, HttpSession> findByPrincipalName(String username) {
        return new HashMap<>(byPrincipal.getOrDefault(username, Map.of()));
    }

    public void deleteById(String id) {
        String username = principalOf.remove(id);
        if (username == null) return;
        HttpSession session = byPrincipal.getOrDefault(username, new HashMap<>()).remove(id);
        if (session != null) {
            try {
                session.invalidate();
            } catch (IllegalStateException alreadyInvalid) {
                // 이미 로그아웃·만료된 세션
            }
        }
    }

    @Override
    public void sessionDestroyed(HttpSessionEvent se) {
        String id = se.getSession().getId();
        String username = principalOf.remove(id);
        if (username != null) {
            byPrincipal.getOrDefault(username, new HashMap<>()).remove(id);
        }
    }
}
