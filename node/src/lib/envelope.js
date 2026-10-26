// 5-2 저장 데이터 암호화: KMS 봉투 암호화(AES-256-GCM) + 검색용 블라인드 인덱스.
// kms 는 AWS SDK 의 KMSClient 또는 같은 모양의 가짜(lib/local-kms.js). 키는 코드에 두지 않는다.
import { createCipheriv, createDecipheriv, createHmac, randomBytes } from "node:crypto";
import { DecryptCommand, GenerateDataKeyCommand } from "@aws-sdk/client-kms";

export const normalizePhone = (v) => String(v ?? "").replace(/\D/g, "");

export function createFieldCrypto({ kms, keyId, indexKey }) {
  if (!indexKey || indexKey.length < 32) throw new Error("블라인드 인덱스 키(32바이트 이상)가 필요합니다");

  // ① 데이터 키 발급 → ② AES-GCM(매번 새 IV) → ③ 암호문 + IV + 태그 + 감싼 키를 함께 저장
  async function encrypt(plaintext) {
    const { Plaintext, CiphertextBlob } =
      await kms.send(new GenerateDataKeyCommand(
        { KeyId: keyId, KeySpec: "AES_256" }));
    const key = Buffer.from(Plaintext);
    try {
      const iv = randomBytes(12);
      const c = createCipheriv(
        "aes-256-gcm", key, iv);
      const ct = Buffer.concat(
        [c.update(String(plaintext), "utf8"), c.final()]);
      return { ct, iv, tag: c.getAuthTag(),
        wrappedKey: Buffer.from(CiphertextBlob) };
    } finally {
      key.fill(0); // 평문 키는 쓰고 바로 버린다
      if (Plaintext instanceof Uint8Array) Plaintext.fill(0);
    }
  }

  // 읽을 때는 감싼 키를 KMS 에 Decrypt 요청 → 태그가 맞지 않으면(변조) 예외
  async function decrypt({ ct, iv, tag, wrappedKey }) {
    const { Plaintext } = await kms.send(new DecryptCommand({ KeyId: keyId, CiphertextBlob: wrappedKey }));
    const key = Buffer.from(Plaintext);
    try {
      const d = createDecipheriv("aes-256-gcm", key, iv);
      d.setAuthTag(tag);
      return Buffer.concat([d.update(ct), d.final()]).toString("utf8");
    } finally {
      key.fill(0);
    }
  }

  // 같은 번호로 찾아야 할 때: HMAC(인덱스 키, 정규화한 번호). 인덱스 키는 암호화 키와 따로 둔다
  function blindIndex(phone) {
    return createHmac("sha256", indexKey)
      .update(normalizePhone(phone))
      .digest("hex");
  }

  return { encrypt, decrypt, blindIndex };
}

// DB 컬럼(문자열)과 오가는 변환: phone_ct · phone_iv · phone_tag · phone_key
export const toColumns = ({ ct, iv, tag, wrappedKey }) => ({
  phoneCt: ct.toString("base64"),
  phoneIv: iv.toString("base64"),
  phoneTag: tag.toString("base64"),
  phoneKey: wrappedKey.toString("base64"),
});
export const fromColumns = (m) => ({
  ct: Buffer.from(m.phoneCt, "base64"),
  iv: Buffer.from(m.phoneIv, "base64"),
  tag: Buffer.from(m.phoneTag, "base64"),
  wrappedKey: Buffer.from(m.phoneKey, "base64"),
});
