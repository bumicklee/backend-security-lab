package board;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

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

/**
 * 취약한 /lab/** 가 정말 취약한지 보여 주는 대조 테스트.
 * 같은 입력이 /api/** 에서는 막히고 /lab/** 에서는 통과한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "lab-vulnerable"})
class LabVulnerableDemoTest {
    @Autowired MockMvc mvc;
    @Autowired board.user.AppUserRepository users;
    @Autowired PostRepository posts;

    @Test
    @DisplayName("[lab] 항상 참인 조건이 모든 글을 꺼낸다")
    void sqlInjectionWorksOnLab() throws Exception {
        mvc.perform(get("/lab/posts").param("title", "' OR 1=1--"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(3)));
    }

    @Test
    @DisplayName("[lab] UNION 으로 회원 이메일이 섞여 나온다")
    void unionWorksOnLab() throws Exception {
        mvc.perform(get("/lab/posts").param("title", "' UNION SELECT id, email, password_hash FROM users--"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("admin@example.com")));
    }

    @Test
    @DisplayName("[lab] JPQL 결합도 항상 참 조건이 통한다")
    void jpqlInjectionWorksOnLab() throws Exception {
        mvc.perform(get("/lab/posts/jpql").param("title", "x' OR '1'='1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(3)));
    }

    @Test
    @DisplayName("[lab] @Valid 가 없으면 음수 수량 주문이 만들어진다")
    void negativeQtyOnLab() throws Exception {
        mvc.perform(post("/lab/orders").contentType(MediaType.APPLICATION_JSON).content("{\"productId\":1,\"qty\":-5}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.total").value(-60000));
    }

    @Test
    @DisplayName("[lab] 주인 확인이 없으면 남의 주문(1002 bob)이 그대로 보인다")
    void idorOnLab() throws Exception {
        mvc.perform(get("/lab/orders/1002"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.user").value("bob"))
            .andExpect(jsonPath("$.total").value(30000));
    }

    @Test
    @DisplayName("[lab] 필터 없는 ObjectInputStream 은 아무 클래스나 만든다")
    void unfilteredDeserializationOnLab() throws Exception {
        var out = new ByteArrayOutputStream();
        try (var oos = new ObjectOutputStream(out)) {
            oos.writeObject(new HashMap<>());
        }
        mvc.perform(post("/lab/carts/legacy")
                .contentType(MediaType.parseMediaType("application/x-java-serialized-object"))
                .content(out.toByteArray()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.createdClass").value("java.util.HashMap"));
    }

    @Test
    @DisplayName("[lab] 서명 없는 웹훅은 같은 이벤트를 두 번 반영한다")
    void webhookReplayOnLab() throws Exception {
        String body = "{\"id\":\"evt_lab_1\",\"type\":\"payment.succeeded\",\"orderId\":42,\"amount\":12000}";
        mvc.perform(post("/lab/webhooks/payments").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk());
        mvc.perform(post("/lab/webhooks/payments").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.appliedCount").value(2));
    }

    @Test
    @DisplayName("[lab] 엔티티로 받은 본문이 role 까지 복사되고 응답에 해시가 나간다")
    void massAssignmentOnLab() throws Exception {
        try {
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/lab/users/me")
                    .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("bob"))
                    .contentType(MediaType.APPLICATION_JSON).content("{\"nickname\":\"밥\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.passwordHash").exists())
                .andExpect(jsonPath("$.resetToken").exists());
        } finally {
            var bob = users.findByUsername("bob").orElseThrow();
            bob.setRole("USER");                       // 같은 컨텍스트를 쓰는 다른 테스트를 위해 되돌린다
            users.save(bob);
        }
    }

    @Test
    @DisplayName("[lab] th:utext 는 저장된 태그를 그대로 출력한다")
    void storedXssOnLab() throws Exception {
        long id = posts.save(new Post("이벤트 안내", "<img src=x onerror=alert(document.cookie)>", "bob")).getId();
        mvc.perform(get("/lab/posts/{id}/view", id))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString(
                "<div class=\"content\"><img src=x onerror=alert(document.cookie)></div>")));
    }
}
