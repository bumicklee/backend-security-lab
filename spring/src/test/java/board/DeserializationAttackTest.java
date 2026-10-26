package board;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import board.cart.LegacyCart;
import board.cart.LegacyCart.LegacyCartItem;
import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.util.HashMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** 2-7 역직렬화: JSON DTO·이름 있는 하위 타입·ObjectInputFilter 허용 목록 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DeserializationAttackTest {
    @Autowired MockMvc mvc;

    static final MediaType JAVA_SERIALIZED = MediaType.parseMediaType("application/x-java-serialized-object");

    ResultActions postJson(String url, String json) throws Exception {
        return mvc.perform(post(url).with(user("alice")).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    static byte[] serialize(Object o) throws Exception {
        var out = new ByteArrayOutputStream();
        try (var oos = new ObjectOutputStream(out)) {
            oos.writeObject(o);
        }
        return out.toByteArray();
    }

    @Test
    @DisplayName("@class 필드로 임의 클래스를 만들 수 없다")
    void classFieldIsRejected() throws Exception {
        postJson("/api/payments", "{\"@class\":\"java.net.URL\",\"type\":\"card\",\"cardNo\":\"4111111111111111\"}")
            .andExpect(status().isBadRequest());
        postJson("/api/payments", "{\"@class\":\"java.util.HashMap\"}")
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("모르는 type 이름은 400")
    void unknownTypeName() throws Exception {
        postJson("/api/payments", "{\"type\":\"java.lang.ProcessBuilder\",\"cardNo\":\"4111111111111111\"}")
            .andExpect(status().isBadRequest());
        postJson("/api/payments", "{\"type\":\"crypto\",\"wallet\":\"0xEXAMPLE\"}")
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("정해진 type 이름은 통과하고 응답에는 끝 4자리만")
    void knownTypeName() throws Exception {
        postJson("/api/payments", "{\"type\":\"card\",\"cardNo\":\"4111111111111111\"}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.masked").value("****-****-****-1111"));
    }

    @Test
    @DisplayName("JSON 장바구니는 구체 DTO 로만 받는다")
    void jsonCart() throws Exception {
        postJson("/api/carts", "{\"items\":[{\"productId\":1,\"qty\":2}]}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalQty").value(2));
        postJson("/api/carts", "{\"items\":[{\"productId\":1,\"qty\":2,\"@class\":\"java.util.HashMap\"}]}")
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("ObjectInputFilter 는 허용 목록 밖 클래스를 거절한다")
    void filterRejectsUnexpectedClass() throws Exception {
        var unexpected = new HashMap<String, String>();
        unexpected.put("k", "v");
        mvc.perform(post("/api/carts/legacy").with(user("alice")).with(csrf())
                .contentType(JAVA_SERIALIZED).content(serialize(unexpected)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("허용 목록에 있는 LegacyCart 는 통과한다")
    void filterAllowsLegacyCart() throws Exception {
        var cart = new LegacyCart(new LegacyCartItem(1, 2), new LegacyCartItem(2, 3));
        mvc.perform(post("/api/carts/legacy").with(user("alice")).with(csrf())
                .contentType(JAVA_SERIALIZED).content(serialize(cart)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalQty").value(5));
    }
}
