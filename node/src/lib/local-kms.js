// 실습·테스트용 가짜 KMS. AWS SDK 클라이언트와 같은 모양(send(command))이라
// 수업 화면의 코드(GenerateDataKeyCommand · DecryptCommand · SignCommand)를 그대로 돌려 볼 수 있다.
// 진짜 KMS 와 달리 마스터 키·서명 키가 이 프로세스 메모리에 있다 → 운영에서는 쓰지 않는다 (KMS_KEY_ID 설정).
import { createCipheriv, createDecipheriv, generateKeyPairSync, randomBytes, sign } from "node:crypto";
import { DecryptCommand, GenerateDataKeyCommand, GetPublicKeyCommand, SignCommand } from "@aws-sdk/client-kms";

const MAGIC = Buffer.from("LKMS1");

export function createLocalKms({ masterKey = randomBytes(32), signingKey } = {}) {
  if (masterKey.length !== 32) throw new Error("masterKey 는 32바이트여야 합니다");
  const rsa = signingKey ?? generateKeyPairSync("rsa", { modulusLength: 2048 });
  const calls = []; // 테스트에서 어떤 요청이 왔는지 확인한다

  // 데이터 키를 마스터 키로 감싼다: MAGIC | IV(12) | 태그(16) | 감싼 키
  const wrap = (dataKey) => {
    const iv = randomBytes(12);
    const c = createCipheriv("aes-256-gcm", masterKey, iv);
    const ct = Buffer.concat([c.update(dataKey), c.final()]);
    return Buffer.concat([MAGIC, iv, c.getAuthTag(), ct]);
  };
  const unwrap = (blob) => {
    const b = Buffer.from(blob);
    if (!b.subarray(0, MAGIC.length).equals(MAGIC)) throw kmsError("InvalidCiphertextException");
    const iv = b.subarray(5, 17);
    const tag = b.subarray(17, 33);
    const d = createDecipheriv("aes-256-gcm", masterKey, iv);
    d.setAuthTag(tag);
    try {
      return Buffer.concat([d.update(b.subarray(33)), d.final()]);
    } catch {
      throw kmsError("InvalidCiphertextException");
    }
  };

  return {
    calls,
    publicKey: rsa.publicKey,
    // 테스트에서 "키가 유출된 상황"을 재현할 때만 쓴다
    leakSigningKeyForTest: () => rsa.privateKey,
    async send(command) {
      calls.push({ name: command.constructor.name, input: command.input });
      const input = command.input;
      if (command instanceof GenerateDataKeyCommand) {
        if (input.KeySpec !== "AES_256") throw kmsError("ValidationException");
        const Plaintext = randomBytes(32);
        return { KeyId: input.KeyId, Plaintext, CiphertextBlob: wrap(Plaintext) };
      }
      if (command instanceof DecryptCommand) {
        return { KeyId: input.KeyId, Plaintext: unwrap(input.CiphertextBlob) };
      }
      if (command instanceof SignCommand) {
        if (input.SigningAlgorithm !== "RSASSA_PKCS1_V1_5_SHA_256") throw kmsError("ValidationException");
        if ((input.MessageType ?? "RAW") !== "RAW") throw kmsError("ValidationException");
        return { KeyId: input.KeyId, Signature: sign("sha256", Buffer.from(input.Message), rsa.privateKey) };
      }
      if (command instanceof GetPublicKeyCommand) {
        return { KeyId: input.KeyId, PublicKey: rsa.publicKey.export({ type: "spki", format: "der" }) };
      }
      throw new Error(`가짜 KMS 가 지원하지 않는 요청: ${command.constructor.name}`);
    },
  };
}

function kmsError(name) {
  const e = new Error(name);
  e.name = name;
  return e;
}
