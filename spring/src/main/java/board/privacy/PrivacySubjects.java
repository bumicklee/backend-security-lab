package board.privacy;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

/** 5-4 핸들러가 처리한 정보주체(회원 id)를 접속기록 인터셉터에 넘긴다 (Express 의 res.locals.subjectIds) */
public final class PrivacySubjects {
    static final String ATTR = PrivacySubjects.class.getName();

    private PrivacySubjects() {}

    public static void set(HttpServletRequest request, List<Long> ids) {
        request.setAttribute(ATTR, ids);
    }
}
