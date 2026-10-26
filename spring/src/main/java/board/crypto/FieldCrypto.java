package board.crypto;

import static java.nio.charset.StandardCharsets.UTF_8;
import static javax.crypto.Cipher.DECRYPT_MODE;
import static javax.crypto.Cipher.ENCRYPT_MODE;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HexFormat;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.DataKeySpec;

/**
 * 5-2 저장 데이터 암호화: KMS 봉투 암호화(AES-256-GCM, 매번 새 12바이트 IV, 128비트 태그) + 블라인드 인덱스.
 * 키는 코드에 없다. 데이터 키는 KMS 가 발급하고, 감싼 키(ciphertextBlob)를 암호문과 함께 저장한다.
 */
public class FieldCrypto {

    /** 함께 저장하는 값: 암호문(+GCM 태그) · IV · 감싼 데이터 키 */
    public record Encrypted(byte[] ct, byte[] iv, byte[] wrappedKey) {}

    private final KmsClient kms;
    private final String keyArn;
    private final byte[] indexKey;
    private final SecureRandom random = new SecureRandom();

    public FieldCrypto(KmsClient kms, String keyArn, byte[] indexKey) {
        if (indexKey.length < 32) throw new IllegalArgumentException("블라인드 인덱스 키는 32바이트 이상");
        this.kms = kms;
        this.keyArn = keyArn;
        this.indexKey = indexKey.clone();
    }

    public Encrypted encrypt(String phone) {
        var dk = kms.generateDataKey(r -> r
            .keyId(keyArn).keySpec(DataKeySpec.AES_256));
        byte[] key = dk.plaintext().asByteArray();
        try {
            byte[] iv = new byte[12];
            random.nextBytes(iv);   // 매번 새 IV
            Cipher c = Cipher.getInstance(
                "AES/GCM/NoPadding");
            c.init(ENCRYPT_MODE, aesKey(key),
                new GCMParameterSpec(128, iv));
            byte[] ct = c.doFinal(phone.getBytes(UTF_8));
            return new Encrypted(ct, iv, dk.ciphertextBlob().asByteArray());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("encrypt failed", e);
        } finally {
            Arrays.fill(key, (byte) 0);   // 평문 키는 쓰고 바로 버린다
        }
    }

    /** 감싼 키를 KMS 에 Decrypt → 태그가 맞지 않으면(변조) AEADBadTagException */
    public String decrypt(Encrypted e) throws GeneralSecurityException {
        byte[] key = kms.decrypt(r -> r.keyId(keyArn)
            .ciphertextBlob(SdkBytes.fromByteArray(e.wrappedKey()))).plaintext().asByteArray();
        try {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(DECRYPT_MODE, aesKey(key), new GCMParameterSpec(128, e.iv()));
            return new String(c.doFinal(e.ct()), UTF_8);
        } finally {
            Arrays.fill(key, (byte) 0);
        }
    }

    /** 같은 번호로 찾아야 할 때: HMAC-SHA256(인덱스 키, 숫자만). 인덱스 키는 암호화 키와 따로 */
    public String blindIndex(String phone) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(indexKey, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(normalize(phone).getBytes(UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String normalize(String phone) {
        return phone == null ? "" : phone.replaceAll("\\D", "");
    }

    private static SecretKeySpec aesKey(byte[] key) {
        return new SecretKeySpec(key, "AES");
    }
}
