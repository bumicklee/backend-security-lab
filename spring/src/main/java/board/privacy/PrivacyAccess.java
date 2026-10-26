package board.privacy;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 5-4 이 핸들러는 개인정보를 처리한다 → {@link PrivacyAccessInterceptor} 가 접속기록 다섯 항목을 남긴다.
 * 값은 수행업무(조회·검색·다운로드 …). 처리한 정보주체는 핸들러가 {@link PrivacySubjects#set} 으로 알려 준다.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface PrivacyAccess {
    String value();
}
