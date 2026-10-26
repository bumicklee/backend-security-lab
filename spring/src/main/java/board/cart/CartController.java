package board.cart;

import jakarta.validation.Valid;
import java.io.IOException;
import java.io.InputStream;
import java.io.InvalidClassException;
import java.util.Arrays;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/carts")
public class CartController {

    /** 권장: JSON 을 구체 DTO 로. 만들 클래스는 코드가 정한다. */
    @PostMapping(consumes = "application/json")
    Map<String, Object> create(@Valid @RequestBody Cart cart) {
        int qty = cart.items().stream().mapToInt(Cart.Item::qty).sum();
        return Map.of("items", cart.items().size(), "totalQty", qty);
    }

    /** 레거시 호환: Java 직렬화 본문. 허용 목록 필터를 통과한 클래스만 만든다. */
    @PostMapping(value = "/legacy", consumes = "application/x-java-serialized-object")
    Map<String, Object> importLegacy(InputStream body) throws IOException {
        try {
            LegacyCart cart = SafeObjectInput.readCart(body);
            if (cart.items == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
            int qty = Arrays.stream(cart.items).mapToInt(i -> i.qty).sum();
            return Map.of("items", cart.items.length, "totalQty", qty);
        } catch (InvalidClassException | ClassNotFoundException | ClassCastException e) {
            // 필터가 거절했거나 예상과 다른 타입 → 400
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "rejected");
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "malformed");
        }
    }
}
