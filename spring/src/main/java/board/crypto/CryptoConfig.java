package board.crypto;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.kms.KmsClient;

/**
 * 5-2 KMS 연결. {@code app.crypto.kms-key-id}(KMS_KEY_ID)가 있으면 실제 KMS(서버 역할 권한),
 * 없으면 프로세스 안의 가짜 KMS({@link LocalKmsClient}, 실습 전용).
 */
@Configuration
public class CryptoConfig {
    private static final Logger log = LoggerFactory.getLogger(CryptoConfig.class);

    @Bean(destroyMethod = "close")
    KmsClient kmsClient(@Value("${app.crypto.kms-key-id:}") String keyId,
            @Value("${app.crypto.region}") String region,
            @Value("${app.crypto.local-master-secret}") String localMasterSecret) {
        if (!keyId.isBlank()) {
            // 자격 증명은 기본 체인 (인스턴스 역할 · IRSA). 키 파일은 쓰지 않는다
            return KmsClient.builder().region(Region.of(region)).build();
        }
        log.warn("KMS_KEY_ID 가 없어 가짜 KMS(LocalKmsClient)로 암호화합니다. 실습 전용");
        return new LocalKmsClient(sha256(localMasterSecret));
    }

    @Bean
    FieldCrypto fieldCrypto(KmsClient kms,
            @Value("${app.crypto.kms-key-id:}") String keyId,
            @Value("${app.crypto.phone-index-key}") String indexKey) {
        return new FieldCrypto(kms, keyId.isBlank() ? "alias/board-local" : keyId, indexKey.getBytes(UTF_8));
    }

    private static byte[] sha256(String s) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(s.getBytes(UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
