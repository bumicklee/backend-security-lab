package board.member;

import board.privacy.PrivacyAccess;
import board.privacy.PrivacySubjects;
import board.support.Mask;
import jakarta.servlet.http.HttpServletRequest;
import java.security.GeneralSecurityException;
import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 관리자 회원 기능 (URL 규칙 /api/admin/** → ADMIN + @PreAuthorize).
 * 5-2 전화번호 검색은 블라인드 인덱스 · 5-4 볼 때마다 접속기록(@PrivacyAccess) · 5-5 내보내기는 컬럼·건수 제한 + 누적 감시
 */
@RestController
@RequestMapping("/api/admin/members")
@PreAuthorize("hasRole('ADMIN')")
public class AdminMemberController {
    static final int PAGE = 1000;
    /** 내보낼 수 있는 컬럼 허용 목록 (암호문·인덱스는 없다) */
    static final Map<String, Function<Member, Object>> EXPORTABLE = Map.of(
        "id", Member::getId,
        "nickname", Member::getNickname,
        "email", Member::getEmail,
        "createdAt", Member::getCreatedAt);

    private final MemberService service;
    private final MemberRepository members;
    private final ExportCounter exports;
    private final ExportAlert alerts;
    private final long alertThreshold;

    public AdminMemberController(MemberService service, MemberRepository members, ExportCounter exports,
            ExportAlert alerts, @Value("${app.export.alert-threshold}") long alertThreshold) {
        this.service = service;
        this.members = members;
        this.exports = exports;
        this.alerts = alerts;
        this.alertThreshold = alertThreshold;
    }

    @PrivacyAccess("회원 조회")
    @GetMapping("/{id}")
    ResponseEntity<Map<String, Object>> show(@PathVariable long id, HttpServletRequest req)
            throws GeneralSecurityException {
        var m = service.find(id).orElse(null);
        if (m == null) return ResponseEntity.notFound().build();
        PrivacySubjects.set(req, List.of(m.getId()));
        return ResponseEntity.ok(view(m));
    }

    @PrivacyAccess("회원 검색")
    @GetMapping(params = "phone")
    ResponseEntity<List<Map<String, Object>>> search(@RequestParam String phone, HttpServletRequest req)
            throws GeneralSecurityException {
        if (!phone.matches("[0-9-]{9,13}")) return ResponseEntity.badRequest().build();
        var rows = service.findByPhone(phone);
        PrivacySubjects.set(req, rows.stream().map(Member::getId).toList());
        var out = new java.util.ArrayList<Map<String, Object>>();
        for (Member m : rows) out.add(view(m));
        return ResponseEntity.ok(out);
    }

    @PrivacyAccess("회원 다운로드")
    @GetMapping("/export")
    ResponseEntity<List<Map<String, Object>>> export(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "id,nickname") String fields, Principal who, HttpServletRequest req) {
        Set<String> cols = new LinkedHashSet<>(List.of("id"));
        for (String f : fields.split(",")) {
            if (!f.isBlank()) cols.add(f.trim());
        }
        if (page < 0 || !EXPORTABLE.keySet().containsAll(cols) || fields.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        var rows = members.findByWithdrawnAtIsNull(
            PageRequest.of(page, PAGE, Sort.by("id").ascending()));
        PrivacySubjects.set(req, rows.stream().map(Member::getId).toList());
        long total = exports.add(who.getName(), rows.size());   // 오늘 누적 건수
        if (total > alertThreshold) alerts.alert("bulk-export", who.getName(), total);
        return ResponseEntity.ok(rows.stream().map(m -> {
            Map<String, Object> row = new LinkedHashMap<>();
            cols.forEach(c -> row.put(c, EXPORTABLE.get(c).apply(m)));
            return row;
        }).toList());
    }

    /** 복호화해도 화면에는 가려서 */
    private Map<String, Object> view(Member m) throws GeneralSecurityException {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("id", m.getId());
        v.put("nickname", m.getNickname());
        v.put("email", Mask.email(m.getEmail()));
        v.put("phone", Mask.phone(service.phoneOf(m)));
        return v;
    }
}
