package board.crypto;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.KmsServiceClientConfiguration;
import software.amazon.awssdk.services.kms.model.DataKeySpec;
import software.amazon.awssdk.services.kms.model.DecryptRequest;
import software.amazon.awssdk.services.kms.model.DecryptResponse;
import software.amazon.awssdk.services.kms.model.GenerateDataKeyRequest;
import software.amazon.awssdk.services.kms.model.GenerateDataKeyResponse;
import software.amazon.awssdk.services.kms.model.InvalidCiphertextException;

/**
 * 실습·테스트용 가짜 KMS. AWS SDK 의 {@link KmsClient} 를 그대로 구현하므로 수업 화면의
 * {@code kms.generateDataKey(r -> r.keyId(KEY_ARN).keySpec(AES_256))} 코드가 네트워크 없이 돈다.
 * 진짜 KMS 와 달리 마스터 키가 이 프로세스 메모리에 있다 → 운영은 {@code app.crypto.kms-key-id} 로 실제 KMS 를 쓴다.
 */
public class LocalKmsClient implements KmsClient {
    private static final byte[] MAGIC = "LKMS1".getBytes();
    private final byte[] masterKey;
    private final SecureRandom random = new SecureRandom();
    /** 테스트에서 어떤 요청이 왔는지 확인한다 */
    public final List<Object> requests = new ArrayList<>();

    public LocalKmsClient(byte[] masterKey) {
        if (masterKey.length != 32) throw new IllegalArgumentException("masterKey 는 32바이트여야 합니다");
        this.masterKey = masterKey.clone();
    }

    @Override
    public GenerateDataKeyResponse generateDataKey(GenerateDataKeyRequest req) {
        requests.add(req);
        if (req.keySpec() != DataKeySpec.AES_256) throw new IllegalArgumentException("AES_256 만 지원");
        byte[] dataKey = new byte[32];
        random.nextBytes(dataKey);
        return GenerateDataKeyResponse.builder().keyId(req.keyId())
            .plaintext(SdkBytes.fromByteArray(dataKey))
            .ciphertextBlob(SdkBytes.fromByteArray(wrap(dataKey))).build();
    }

    @Override
    public DecryptResponse decrypt(DecryptRequest req) {
        requests.add(req);
        return DecryptResponse.builder().keyId(req.keyId())
            .plaintext(SdkBytes.fromByteArray(unwrap(req.ciphertextBlob().asByteArray()))).build();
    }

    // 감싼 키: MAGIC | IV(12) | AES-GCM(마스터 키, 데이터 키) + 태그
    private byte[] wrap(byte[] dataKey) {
        try {
            byte[] iv = new byte[12];
            random.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(masterKey, "AES"), new GCMParameterSpec(128, iv));
            byte[] ct = c.doFinal(dataKey);
            byte[] out = new byte[MAGIC.length + 12 + ct.length];
            System.arraycopy(MAGIC, 0, out, 0, MAGIC.length);
            System.arraycopy(iv, 0, out, MAGIC.length, 12);
            System.arraycopy(ct, 0, out, MAGIC.length + 12, ct.length);
            return out;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private byte[] unwrap(byte[] blob) {
        if (blob.length < MAGIC.length + 12 + 16
                || !Arrays.equals(Arrays.copyOf(blob, MAGIC.length), MAGIC)) {
            throw InvalidCiphertextException.builder().message("InvalidCiphertextException").build();
        }
        try {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(masterKey, "AES"),
                new GCMParameterSpec(128, blob, MAGIC.length, 12));
            return c.doFinal(blob, MAGIC.length + 12, blob.length - MAGIC.length - 12);
        } catch (GeneralSecurityException e) {
            throw InvalidCiphertextException.builder().message("InvalidCiphertextException").build();
        }
    }

    @Override public String serviceName() { return "kms"; }
    @Override public KmsServiceClientConfiguration serviceClientConfiguration() { return null; }
    @Override public void close() {}
}
