package board;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** 2-2 입력 검증: Bean Validation(@Valid) 으로 경계에서 거절 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderValidationTest {
    @Autowired MockMvc mvc;

    ResultActions order(String json) throws Exception {
        return mvc.perform(post("/api/orders").with(user("alice")).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    @DisplayName("수량이 0 이하이면 400")
    void qtyMustBePositive() throws Exception {
        order("{\"productId\":1,\"qty\":-5}").andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("qty"));
        order("{\"productId\":1,\"qty\":0}").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("쿠폰 형식이 다르면 400")
    void couponFormat() throws Exception {
        order("{\"productId\":1,\"qty\":1,\"coupon\":\"' OR 1=1\"}").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("문자열·배열·모르는 필드가 오면 400 (타입 혼동)")
    void typeConfusion() throws Exception {
        order("{\"productId\":\"7\",\"qty\":[2]}").andExpect(status().isBadRequest());
        order("{\"productId\":1,\"qty\":1,\"role\":\"ADMIN\"}").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("올바른 주문은 201, 금액은 서버 가격으로 계산")
    void validOrder() throws Exception {
        order("{\"productId\":1,\"qty\":2,\"coupon\":\"WELCOME1\"}")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.total").value(24000));
    }
}
