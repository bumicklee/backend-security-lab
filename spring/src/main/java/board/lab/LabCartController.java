/*
 * ================================================================
 *  의도적으로 취약한 실습 코드 — 운영에 쓰지 마세요
 *  lab-vulnerable 프로필 + server.address=127.0.0.1 에서만 켜집니다.
 *  안전한 구현은 같은 기능의 /api/** 쪽 클래스를 보세요.
 * ================================================================
 */
package board.lab;

import java.io.InputStream;
import java.io.ObjectInputStream;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 2-7 역직렬화: 필터 없는 ObjectInputStream.
 * readObject() 는 캐스팅하기 "전에" 스트림이 지정한 아무 클래스나 만든다.
 * 이 예제는 어떤 클래스가 만들어졌는지만 돌려준다. 안전한 쪽은 board.cart.CartController
 */
@RestController
@Profile("lab-vulnerable")
public class LabCartController {

    @PostMapping(value = "/lab/carts/legacy", consumes = "application/x-java-serialized-object")
    Map<String, String> importLegacy(InputStream body) throws Exception {
        // ✗ Java 기본 직렬화, 허용 목록 없음
        var in = new ObjectInputStream(body);
        Object o = in.readObject();
        return Map.of("createdClass", o.getClass().getName());
    }
}
