package board;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import board.crypto.FieldCrypto;
import board.crypto.FieldCrypto.Encrypted;
import board.crypto.LocalKmsClient;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.AEADBadTagException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.kms.model.DataKeySpec;
import software.amazon.awssdk.services.kms.model.DecryptRequest;
import software.amazon.awssdk.services.kms.model.GenerateDataKeyRequest;
import software.amazon.awssdk.services.kms.model.InvalidCiphertextException;

/** 5-2 봉투 암호화(KMS 데이터 키 + AES/GCM/NoPadding) · 블라인드 인덱스. 가짜 KMS 로 네트워크 없이 */
class EnvelopeEncryptionTest {
    static final String KEY_ARN = "arn:aws:kms:ap-northeast-2:111122223333:key/1234abcd-12ab-34cd-56ef-1234567890ab";
    static final byte[] INDEX_KEY = new byte[32];
    static { Arrays.fill(INDEX_KEY, (byte) 7); }

    static byte[] randomKey() {
        byte[] k = new byte[32];
        new SecureRandom().nextBytes(k);
        return k;
    }

    final LocalKmsClient kms = new LocalKmsClient(randomKey());
    final FieldCrypto crypto = new FieldCrypto(kms, KEY_ARN, INDEX_KEY);

    @Test
    @DisplayName("암호화 → 복호화하면 원래 값, 데이터 키는 KMS 에 AES_256 으로 요청한다")
    void roundTrip() throws Exception {
        Encrypted e = crypto.encrypt("010-1234-5678");
        assertThat(e.iv()).hasSize(12);
        assertThat(e.ct()).hasSize("010-1234-5678".length() + 16);   // + 128비트 GCM 태그
        assertThat(crypto.decrypt(e)).isEqualTo("010-1234-5678");
        var req = (GenerateDataKeyRequest) kms.requests.get(0);
        assertThat(req.keyId()).isEqualTo(KEY_ARN);
        assertThat(req.keySpec()).isEqualTo(DataKeySpec.AES_256);
        assertThat(kms.requests.get(1)).isInstanceOf(DecryptRequest.class);
    }

    @Test
    @DisplayName("같은 번호도 매번 새 IV · 새 데이터 키라 암호문이 다르다")
    void freshIvEveryTime() {
        Encrypted a = crypto.encrypt("010-1234-5678");
        Encrypted b = crypto.encrypt("010-1234-5678");
        assertThat(a.iv()).isNotEqualTo(b.iv());
        assertThat(a.ct()).isNotEqualTo(b.ct());
        assertThat(a.wrappedKey()).isNotEqualTo(b.wrappedKey());
    }

    @Test
    @DisplayName("암호문을 바꾸면 GCM 태그 검증에 실패하고, 감싼 키를 바꾸면 KMS 가 거절한다")
    void tamperDetected() {
        Encrypted e = crypto.encrypt("010-1234-5678");
        byte[] ct = e.ct().clone();
        ct[0] ^= 1;
        assertThatThrownBy(() -> crypto.decrypt(new Encrypted(ct, e.iv(), e.wrappedKey())))
            .isInstanceOf(AEADBadTagException.class);
        byte[] wk = e.wrappedKey().clone();
        wk[wk.length - 1] ^= 1;
        assertThatThrownBy(() -> crypto.decrypt(new Encrypted(e.ct(), e.iv(), wk)))
            .isInstanceOf(InvalidCiphertextException.class);
    }

    @Test
    @DisplayName("다른 KMS(다른 마스터 키)로는 감싼 키를 풀 수 없다")
    void otherKmsCannotDecrypt() {
        Encrypted e = crypto.encrypt("010-1234-5678");
        var other = new FieldCrypto(new LocalKmsClient(randomKey()), KEY_ARN, INDEX_KEY);
        assertThatThrownBy(() -> other.decrypt(e)).isInstanceOf(InvalidCiphertextException.class);
    }

    @Test
    @DisplayName("블라인드 인덱스: 같은 번호(표기가 달라도)는 같은 값, 다른 번호·다른 키는 다른 값")
    void blindIndex() {
        assertThat(crypto.blindIndex("010-1234-5678")).isEqualTo(crypto.blindIndex("01012345678"))
            .matches("[0-9a-f]{64}");
        assertThat(crypto.blindIndex("010-1234-5678")).isNotEqualTo(crypto.blindIndex("010-1234-5679"));
        byte[] otherKey = INDEX_KEY.clone();
        otherKey[0] = 8;
        assertThat(new FieldCrypto(kms, KEY_ARN, otherKey).blindIndex("010-1234-5678"))
            .isNotEqualTo(crypto.blindIndex("010-1234-5678"));
    }
}
