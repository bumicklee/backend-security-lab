package board;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** 2-8 공격 재현 테스트: CI 에서 PR 마다 돌린다 (./gradlew test --tests '*AttackRegressionTest') */
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class AttackRegressionTest {
  @Autowired MockMvc mvc;

  @ParameterizedTest
  @ValueSource(strings = {"' OR 1=1--",
      "' UNION SELECT id, email, password_hash FROM users--"})
  void sqlPayloadFindsNothing(String keyword) throws Exception {
    mvc.perform(get("/api/posts/search").param("keyword", keyword))
       .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
  }

  @Test void traversalIsRejected() throws Exception {
    mvc.perform(get("/api/files").param("name", "../../etc/passwd"))
       .andExpect(status().isBadRequest());
  }

  @Test void metadataPreviewIsRejected() throws Exception {
    mvc.perform(get("/api/preview").param("url", "http://169.254.169.254/latest/meta-data/"))
       .andExpect(status().isBadRequest());
  }
}
