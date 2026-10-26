package board;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import board.member.ExportAlert;
import board.member.Member;
import board.member.MemberRepository;
import board.privacy.PrivacyAccessLog;
import board.privacy.PrivacyAccessLogRepository;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 5-5 관리자 내보내기: 필요한 컬럼만, 1,000건씩(id 오름차순), 오늘 누적 건수 감시 */
@SpringBootTest(properties = "app.export.alert-threshold=1200")
@AutoConfigureMockMvc @ActiveProfiles("test")
class MemberExportTest {
    @Autowired MockMvc mvc;
    @Autowired MemberRepository members;
    @Autowired PrivacyAccessLogRepository logs;
    @MockitoBean ExportAlert alerts;
    final JsonMapper json = JsonMapper.builder().build();

    @BeforeEach
    void seed() {
        if (members.count() < 1000) {   // 시드 3명(1명 탈퇴) + 1,500명 = 활성 1,502명
            members.saveAll(IntStream.range(0, 1500).mapToObj(i -> new Member("m" + i, "m" + i + "@example.com")).toList());
        }
    }

    JsonNode export(String admin, String query) throws Exception {
        String body = mvc.perform(get("/api/admin/members/export?" + query).with(user(admin).roles("ADMIN")))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    @Test
    @DisplayName("한 번에 1,000건, id 오름차순으로 나눠 내보낸다")
    void pagesOf1000() throws Exception {
        JsonNode p0 = export("admin-a", "page=0");
        assertThat(p0.size()).isEqualTo(1000);
        List<Long> ids = p0.valueStream().map(n -> n.get("id").asLong()).toList();
        assertThat(ids).isSorted();
        JsonNode p1 = export("admin-a", "page=1");
        assertThat(p1.size()).isEqualTo(502);
        assertThat(p1.get(0).get("id").asLong()).isGreaterThan(ids.get(ids.size() - 1));
        assertThat(export("admin-a", "page=2").size()).isZero();
    }

    @Test
    @DisplayName("기본은 id·닉네임만, 허용 목록 밖 컬럼은 400")
    void columnAllowList() throws Exception {
        assertThat(export("admin-b", "page=0").get(0).propertyNames()).containsExactly("id", "nickname");
        assertThat(export("admin-b", "page=0&fields=nickname,email").get(0).propertyNames())
            .containsExactly("id", "nickname", "email");
        for (String f : List.of("phoneCt", "phoneIdx", "nickname,passwordHash")) {
            mvc.perform(get("/api/admin/members/export").param("fields", f).with(user("admin-b").roles("ADMIN")))
                .andExpect(status().isBadRequest());
        }
    }

    @Test
    @DisplayName("오늘 누적 건수가 기준을 넘으면 알리고, 접속기록에 처리 건수를 남긴다")
    void alertsOnBulkExport() throws Exception {
        export("admin-c", "page=0");                               // 누적 1,000
        verify(alerts, never()).alert(anyString(), anyString(), anyLong());
        export("admin-c", "page=1");                               // 누적 1,502 > 1,200
        verify(alerts).alert("bulk-export", "admin-c", 1502);
        List<PrivacyAccessLog> mine = logs.findAllByOrderByIdAsc().stream()
            .filter(l -> l.getActorId().equals("admin-c")).toList();
        assertThat(mine).extracting(PrivacyAccessLog::getTask, PrivacyAccessLog::getCount)
            .containsExactly(org.assertj.core.groups.Tuple.tuple("회원 다운로드", 1000),
                org.assertj.core.groups.Tuple.tuple("회원 다운로드", 502));
    }

    @Test
    @DisplayName("관리자가 아니면 403")
    void nonAdminForbidden() throws Exception {
        mvc.perform(get("/api/admin/members/export").with(user("alice").roles("USER")))
            .andExpect(status().isForbidden());
    }
}
