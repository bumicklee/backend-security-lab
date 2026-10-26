// 비밀번호 해시 (3-1 수업).
// - 새 해시: Argon2id (OWASP 최소값: 메모리 19 MiB, 반복 2, 병렬 1). 형식: $argon2id$v=19$m=19456,p=1,t=2$솔트$해시 (node-argon2 는 m,p,t 순서로 적는다)
// - 예전 해시: Node 내장 scrypt(scrypt$솔트$키) · bcrypt($2b$…). 로그인에 성공하면 Argon2id 로 다시 저장한다.
import { randomBytes, scrypt as scryptCb, timingSafeEqual } from "node:crypto";
import { promisify } from "node:util";
import argon2 from "argon2";
import bcrypt from "bcryptjs";

const scrypt = promisify(scryptCb);

export const OPTS = { type: argon2.argon2id, memoryCost: 19456, timeCost: 2, parallelism: 1 };

// 새 비밀번호 저장: Argon2id (솔트는 argon2 가 계정마다 무작위로 만든다)
export async function hashPassword(password) {
  return argon2.hash(password, OPTS);
}

// 예전 형식(scrypt$…). 시드 데이터가 이 형식이라 해시 이전 실습(3-1 s8)을 재현할 수 있다.
export async function hashScrypt(password) {
  const salt = randomBytes(16);
  const key = await scrypt(password, salt, 32);
  return `scrypt$${salt.toString("hex")}$${key.toString("hex")}`;
}

export async function verifyScrypt(password, stored) {
  if (typeof stored !== "string") return false;
  const [algo, saltHex, keyHex] = stored.split("$");
  if (algo !== "scrypt" || !saltHex || !keyHex) return false;
  const expected = Buffer.from(keyHex, "hex");
  const actual = await scrypt(password, Buffer.from(saltHex, "hex"), expected.length);
  return timingSafeEqual(expected, actual);
}

const isArgon2 = (stored) => stored.startsWith("$argon2");
const isBcrypt = (stored) => /^\$2[aby]\$/.test(stored);

// 예전 형식 검증: 해시 앞부분(접두어)으로 알고리즘을 고른다
async function verifyLegacy(password, stored) {
  if (stored.startsWith("scrypt$")) return verifyScrypt(password, stored);
  if (isBcrypt(stored)) return bcrypt.compare(password, stored);
  return false;
}

// 형식에 맞춰 검증만 한다 (이전 없음)
export async function verifyPassword(password, stored) {
  if (typeof stored !== "string") return false;
  try {
    return isArgon2(stored) ? await argon2.verify(stored, password) : await verifyLegacy(password, stored);
  } catch {
    return false; // 깨진 해시는 실패로 처리
  }
}

// 로그인할 때 검증 + 해시 이전. 평문을 다시 만나는 순간은 로그인뿐이다.
export async function verifyAndUpgrade(users, user, password) {
  if (typeof user?.passwordHash !== "string") return false;
  const legacy = !isArgon2(user.passwordHash);                       // 예전 형식 (scrypt$…, bcrypt $2b$…)
  const ok = legacy ? await verifyLegacy(password, user.passwordHash)
                    : await argon2.verify(user.passwordHash, password).catch(() => false);
  if (ok && (legacy || argon2.needsRehash(user.passwordHash, OPTS))) {
    const passwordHash = await argon2.hash(password, OPTS);
    await users.updateOne({ username: user.username }, { $set: { passwordHash } });
  }
  return ok;
}
