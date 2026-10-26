// 5-7 서명키를 꺼내지 않고, KMS 에 서명을 요청한다 + 발급 기록(issued.save)을 남긴다.
// 키 파일이 서버·노트북에 없으므로 "퇴사자가 들고 나간 서명키" 같은 일이 생기지 않는다.
// 그래도 키가 새었다면, 발급 기록에 없는 jti 는 requireAuth 가 거부한다(0-2·3-2).
import { createPublicKey } from "node:crypto";
import { GetPublicKeyCommand, SignCommand } from "@aws-sdk/client-kms";

const b64url = (v) =>
  Buffer.from(v instanceof Uint8Array ? v : JSON.stringify(v)).toString("base64url");

export function createKmsJwtSigner({ kms, keyId, issued, kid = keyId }) {
  return async function signToken(claims) {
    const header = { alg: "RS256", typ: "JWT", kid };
    const input = b64url(header) + "." +
      b64url(claims);
    const { Signature } = await kms.send(
      new SignCommand({ KeyId: keyId,
        Message: Buffer.from(input),
        MessageType: "RAW",
        SigningAlgorithm:
          "RSASSA_PKCS1_V1_5_SHA_256" }));
    await issued.save(claims.jti, claims.exp, claims.sub);
    return input + "." + b64url(Signature);
  };
}

// 검증용 공개키는 KMS 에서 받아 온다 (DER SPKI → PEM). 공개키는 비밀이 아니다.
export async function kmsPublicKeyPem(kms, keyId) {
  const { PublicKey } = await kms.send(new GetPublicKeyCommand({ KeyId: keyId }));
  return createPublicKey({ key: Buffer.from(PublicKey), format: "der", type: "spki" })
    .export({ type: "spki", format: "pem" });
}
