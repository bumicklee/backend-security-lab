package board.address;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 6-2 내 배송지 조회. 개인정보를 돌려주므로 BulkLookupGuard(계정별 조회량 감시)가 앞에 붙는다. */
@RestController
public class AddressController {
    public record Address(long id, String label, String address) {}

    // 실습용 가짜 배송지
    private static final Map<String, List<Address>> ADDRESSES = Map.of(
        "alice", List.of(new Address(1, "집", "서울시 중구 세종대로 110 (예시)")),
        "bob", List.of(new Address(2, "회사", "부산시 해운대구 센텀중앙로 79 (예시)")));

    @GetMapping("/api/addresses")
    public List<Address> mine(Principal principal) {
        return ADDRESSES.getOrDefault(principal.getName(), List.of());
    }
}
