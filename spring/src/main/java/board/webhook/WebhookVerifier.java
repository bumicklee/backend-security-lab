package board.webhook;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * 결제 웹훅 서명 검증 (4-3).
 * 서명 = HMAC-SHA256(secret, timestamp + "." + 원본 본문), 헤더 형식 "sha256=<hex>"
 * 타임스탬프가 허용 범위(기본 5분)를 벗어나면 재전송으로 보고 거절한다.
 */
@Component
public class WebhookVerifier {
    private final WebhookProperties props;
    private final Clock clock;

    public WebhookVerifier(WebhookProperties props, Clock clock) {
        this.props = props;
        this.clock = clock;
    }

    public boolean verify(byte[] body, String timestamp, String signature) {
        if (body == null || timestamp == null || signature == null || !signature.startsWith("sha256=")) {
            return false;
        }
        long ts;
        try {
            ts = Long.parseLong(timestamp);
        } catch (NumberFormatException e) {
            return false;
        }
        long now = clock.instant().getEpochSecond();
        if (Math.abs(now - ts) > props.toleranceSeconds()) {
            return false;   // 오래된(또는 미래의) 요청 = 재전송 의심
        }
        byte[] expected = sign(props.secret(), timestamp, body).getBytes(StandardCharsets.US_ASCII);
        byte[] actual = signature.getBytes(StandardCharsets.US_ASCII);
        // 문자열 equals 대신 상수 시간 비교 (타이밍 공격 방지)
        return MessageDigest.isEqual(expected, actual);
    }

    /** 서명 생성. 테스트와 실습용 curl 스크립트도 이 형식을 따른다. */
    public static String sign(String secret, String timestamp, byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            mac.update(timestamp.getBytes(StandardCharsets.UTF_8));
            mac.update((byte) '.');
            mac.update(body);
            return "sha256=" + HexFormat.of().formatHex(mac.doFinal());
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException(e);
        }
    }
}
