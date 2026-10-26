package board;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import board.member.Member;
import board.member.MemberRepository;
import board.member.MemberService;
import board.order.Order;
import board.order.OrderRepository;
import board.privacy.PrivacyAccessLog;
import board.privacy.PrivacyAccessLogRepository;
import board.privacy.RetentionJob;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** 5-4 개인정보보호법 관점의 개발: 최소 수집 · 접속기록 다섯 항목 · 보존 기간 파기 배치 */
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class PrivacyDevTest {
    @Autowired MockMvc mvc;
    @Autowired PrivacyAccessLogRepository logs;
    @Autowired MemberRepository members;
    @Autowired MemberService memberService;
    @Autowired OrderRepository orders;
    @Autowired RetentionJob retention;

    static final String OK = """
        {"email":"dave@example.com","password":"dave-lab-pass-1234","nickname":"dave","over14":true}""";

    PrivacyAccessLog lastLog() {
        List<PrivacyAccessLog> all = logs.findAllByOrderByIdAsc();
        return all.get(all.size() - 1);
    }

    @Test
    @DisplayName("가입은 필요한 것만: 주민등록번호·주소가 오면 400, 14세 미만도 400")
    void minimalSignup() throws Exception {
        mvc.perform(post("/api/auth/signup").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(OK))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.username").value("dave@example.com"));
        for (String extra : List.of("\"residentNo\":\"900101-1234567\"", "\"address\":\"서울시 어딘가\"")) {
            String body = OK.replace("\"over14\":true", extra + ",\"over14\":true").replace("dave@", "erin@");
            mvc.perform(post("/api/auth/signup").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/auth/signup").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(OK.replace("true", "false").replace("dave@", "frank@")))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("관리자가 회원을 볼 때 식별자·접속일시·접속지·처리한 정보주체·수행업무를 남긴다")
    void accessLogFiveFields() throws Exception {
        Instant before = Instant.now();
        mvc.perform(get("/api/admin/members/1").with(user("admin").roles("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nickname").value("alice"))
            .andExpect(jsonPath("$.email").value("a***@example.com"))
            .andExpect(jsonPath("$.phone").value("010-****-6789"));
        PrivacyAccessLog e = lastLog();
        assertThat(e.getActorId()).isEqualTo("admin");
        assertThat(e.getAt()).isAfterOrEqualTo(before.minusSeconds(1));
        assertThat(e.getFromIp()).isEqualTo("127.0.0.1");
        assertThat(e.getSubjects()).isEqualTo("1");
        assertThat(e.getTask()).isEqualTo("회원 조회");
        assertThat(e.getStatus()).isEqualTo(200);
        assertThat(e.getCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("전화번호 검색은 블라인드 인덱스로 찾고, DB 에는 평문 번호가 없다")
    void searchByBlindIndex() throws Exception {
        mvc.perform(get("/api/admin/members").param("phone", "01034567890").with(user("admin").roles("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].nickname").value("bob"))
            .andExpect(jsonPath("$[0].phone").value("010-****-7890"));
        assertThat(lastLog().getTask()).isEqualTo("회원 검색");
        Member bob = memberService.findByPhone("010-3456-7890").get(0);
        assertThat(new String(bob.getPhoneCt())).doesNotContain("3456");
    }

    @Test
    @DisplayName("접속기록 저장소에는 삭제 메서드가 없다 (append only)")
    void appendOnly() {
        assertThat(Arrays.stream(PrivacyAccessLogRepository.class.getMethods()).map(m -> m.getName()))
            .noneMatch(n -> n.startsWith("delete"));
    }

    @Test
    @DisplayName("파기 배치: 보존 기간이 지난 탈퇴 회원은 지우고, 오래된 주문은 구매자를 익명화한다")
    void purgeExpired() {
        Member old = memberService.create("old", "old@example.com", "010-9999-0001");
        old.withdraw(Instant.now().minus(Duration.ofDays(400)));
        members.save(old);
        Member recent = memberService.create("recent", "recent@example.com", "010-9999-0002");
        recent.withdraw(Instant.now().minus(Duration.ofDays(1)));
        members.save(recent);
        long oldOrderId = orders.maxId() + 1;
        orders.save(new Order(oldOrderId, "alice", 1, 1, 12_000, null, Instant.now().minus(Duration.ofDays(2200))));

        retention.purgeExpired();

        assertThat(members.findById(old.getId())).isEmpty();
        assertThat(members.findById(recent.getId())).isPresent();
        assertThat(orders.findById(oldOrderId).orElseThrow().getOwner()).isEqualTo("anonymized");
        assertThat(orders.findById(1001L).orElseThrow().getOwner()).isEqualTo("alice");   // 최근 주문은 그대로
    }
}
