package board;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** 3-4 보충: 주인 조건 쿼리 · @PostAuthorize · @PreAuthorize 가 같은 결과를 내는지 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderMethodSecurityTest {
    @Autowired MockMvc mvc;

    @Test
    @DisplayName("주문 응답에는 주인과 금액이 들어 있다 (1001 alice 24000)")
    void ownerSeesOrder() throws Exception {
        mvc.perform(get("/api/orders/1001").with(user("alice")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.user").value("alice"))
            .andExpect(jsonPath("$.total").value(24000));
    }

    @Test
    @DisplayName("없는 주문도 403 (있는지 없는지 알려 주지 않는다)")
    void missingOrderIs403() throws Exception {
        mvc.perform(get("/api/orders/999999").with(user("alice"))).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("@PostAuthorize: 주인은 200, 다른 사용자는 403")
    void postAuthorize() throws Exception {
        mvc.perform(get("/api/v2/orders/1002").with(user("bob"))).andExpect(status().isOk());
        mvc.perform(get("/api/v2/orders/1002").with(user("alice"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v2/orders/999999").with(user("alice"))).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("USER 역할은 관리자 API에서 403, ADMIN 은 목록을 본다")
    void adminUsers() throws Exception {
        mvc.perform(get("/api/admin/users").with(user("alice").roles("USER"))).andExpect(status().isForbidden());
        mvc.perform(delete("/api/admin/users/1").with(user("alice").roles("USER")).with(csrf()))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/users").with(user("admin").roles("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].passwordHash").doesNotExist());
    }
}
