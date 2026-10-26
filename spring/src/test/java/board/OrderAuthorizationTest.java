package board;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** 3-4·3-7 인가 매트릭스: 누가 × 무엇을 × 어떤 결과로 */
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class OrderAuthorizationTest {
  @Autowired MockMvc mvc;                          // 1001번 주문은 alice 것 (테스트 데이터)
  @ParameterizedTest(name = "{0} → {2} = {3}", quoteTextArguments = false) @DisplayName("인가 매트릭스")
  @CsvSource({ "alice, USER, /api/orders/1001, 200", "bob, USER, /api/orders/1001, 403",
               "alice, USER, /api/admin/ping, 403" })
  void matrix(String name, String role, String url, int expected) throws Exception {
    mvc.perform(get(url).with(user(name).roles(role))).andExpect(status().is(expected));
  }
  @Test void anonymousGets401() throws Exception {
    mvc.perform(get("/api/orders/1001")).andExpect(status().isUnauthorized());
  }
}
