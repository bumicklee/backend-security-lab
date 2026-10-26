package board;

import static org.assertj.core.api.Assertions.assertThat;

import board.support.Mask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 0-3 · 5-4 로그 마스킹 (Express mask.test.js 와 같은 모양) */
class MaskTest {
    @Test
    @DisplayName("전화번호·카드번호·이메일 마스킹")
    void masks() {
        assertThat(Mask.phone("010-1234-5678")).isEqualTo("010-****-5678");
        assertThat(Mask.phone("01012345678")).isEqualTo("010-****-5678");
        assertThat(Mask.last4("4111-1111-1111-1111")).isEqualTo("****-****-****-1111");
        assertThat(Mask.email("alice@example.com")).isEqualTo("a***@example.com");
    }
}
