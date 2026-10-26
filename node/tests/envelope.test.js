// 5-2 봉투 암호화(KMS 데이터 키 + AES-256-GCM) · 블라인드 인덱스. 가짜 KMS 로 네트워크 없이 확인한다
import { createFieldCrypto, normalizePhone } from "../src/lib/envelope.js";
import { createLocalKms } from "../src/lib/local-kms.js";

const INDEX_KEY = Buffer.alloc(32, 7);
const KEY_ARN = "arn:aws:kms:ap-northeast-2:111122223333:key/1234abcd-12ab-34cd-56ef-1234567890ab";
const setup = () => {
  const kms = createLocalKms();
  return { kms, crypto: createFieldCrypto({ kms, keyId: KEY_ARN, indexKey: INDEX_KEY }) };
};

test("암호화 → 복호화하면 원래 값, 데이터 키는 KMS 에 AES_256 으로 요청한다", async () => {
  const { kms, crypto } = setup();
  const enc = await crypto.encrypt("010-1234-5678");
  expect(enc.iv).toHaveLength(12);
  expect(enc.tag).toHaveLength(16);
  expect(enc.ct.toString("utf8")).not.toContain("1234");
  expect(await crypto.decrypt(enc)).toBe("010-1234-5678");
  expect(kms.calls[0]).toEqual({ name: "GenerateDataKeyCommand", input: { KeyId: KEY_ARN, KeySpec: "AES_256" } });
  expect(kms.calls[1].name).toBe("DecryptCommand");
});

test("같은 번호도 매번 새 IV · 새 데이터 키라 암호문이 다르다 (ECB 와 다름)", async () => {
  const { crypto } = setup();
  const a = await crypto.encrypt("010-1234-5678");
  const b = await crypto.encrypt("010-1234-5678");
  expect(a.iv.equals(b.iv)).toBe(false);
  expect(a.ct.equals(b.ct)).toBe(false);
  expect(a.wrappedKey.equals(b.wrappedKey)).toBe(false);
});

test("암호문·태그·감싼 키를 바꾸면 복호화가 실패한다 (GCM 무결성)", async () => {
  const { crypto } = setup();
  const enc = await crypto.encrypt("010-1234-5678");
  const flip = (buf) => { const c = Buffer.from(buf); c[0] ^= 1; return c; };
  await expect(crypto.decrypt({ ...enc, ct: flip(enc.ct) })).rejects.toThrow();
  await expect(crypto.decrypt({ ...enc, tag: flip(enc.tag) })).rejects.toThrow();
  await expect(crypto.decrypt({ ...enc, wrappedKey: flip(enc.wrappedKey) })).rejects.toThrow();
});

test("다른 KMS(다른 마스터 키)로는 감싼 키를 풀 수 없다", async () => {
  const { crypto } = setup();
  const enc = await crypto.encrypt("010-1234-5678");
  const other = createFieldCrypto({ kms: createLocalKms(), keyId: KEY_ARN, indexKey: INDEX_KEY });
  await expect(other.decrypt(enc)).rejects.toThrow("InvalidCiphertextException");
});

test("블라인드 인덱스: 같은 번호(표기가 달라도)는 같은 값, 다른 번호·다른 키는 다른 값", () => {
  const { crypto } = setup();
  expect(normalizePhone("010-1234-5678")).toBe("01012345678");
  expect(crypto.blindIndex("010-1234-5678")).toBe(crypto.blindIndex("01012345678"));
  expect(crypto.blindIndex("010-1234-5678")).not.toBe(crypto.blindIndex("010-1234-5679"));
  expect(crypto.blindIndex("010-1234-5678")).toMatch(/^[0-9a-f]{64}$/);
  const otherKey = createFieldCrypto({ kms: createLocalKms(), keyId: KEY_ARN, indexKey: Buffer.alloc(32, 8) });
  expect(otherKey.blindIndex("010-1234-5678")).not.toBe(crypto.blindIndex("010-1234-5678"));
});

test("인덱스 키가 짧으면 시작하지 않는다", () => {
  expect(() => createFieldCrypto({ kms: createLocalKms(), keyId: KEY_ARN, indexKey: Buffer.from("short") })).toThrow();
});
