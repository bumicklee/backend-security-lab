import { createHash, randomBytes, randomUUID } from "node:crypto";
import { createExportCounter } from "../lib/export-guard.js";

// 프로세스 메모리 저장소. 실습용으로 단순하게 둔다.
// 운영에서는 Redis·DB에 두어야 서버가 여러 대여도, 재시작해도 유지된다.

// 발급한 토큰 기록 (jti → 만료 시각) + 사용자별 색인 (3-6 "모든 기기 로그아웃")
export function createIssuedStore(now = () => Date.now()) {
  const m = new Map(); // jti → { exp, sub }
  const bySub = new Map(); // sub → Set<jti>
  const forget = (jti) => {
    const e = m.get(jti);
    if (!e) return;
    m.delete(jti);
    bySub.get(e.sub)?.delete(jti);
  };
  return {
    async add(jti, ttlSec, sub = "") {
      m.set(jti, { exp: now() + ttlSec * 1000, sub });
      if (!bySub.has(sub)) bySub.set(sub, new Set());
      bySub.get(sub).add(jti);
    },
    // 5-7 화면의 issued.save(jti, exp): exp 는 초 단위 만료 시각(JWT exp)
    async save(jti, expSec, sub = "") {
      await this.add(jti, Math.max(0, expSec - now() / 1000), sub);
    },
    async has(jti) {
      const e = m.get(jti);
      if (!e) return false;
      if (e.exp < now()) {
        forget(jti);
        return false;
      }
      return true;
    },
    async delete(jti) {
      forget(jti);
    },
    // 이 사용자에게 발급한 액세스 토큰을 모두 폐기한다
    async deleteAllFor(sub) {
      for (const jti of [...(bySub.get(sub) ?? [])]) forget(jti);
      bySub.delete(sub);
    },
  };
}

// 리프레시 토큰 (3-2). 원문은 클라이언트 쿠키에만 있고, 서버에는 SHA-256 해시만 둔다.
// 같은 로그인에서 이어진 토큰은 family 로 묶어, 재사용이 보이면 계열 전체를 폐기한다.
// 실습용 메모리 구현. 운영이라면 DB·Redis 에 같은 메서드로 구현한다 (consume 은 원자적으로).
export function createRefreshTokenStore({ now = () => Date.now(), ttlSec = 14 * 24 * 3600 } = {}) {
  const byHash = new Map(); // hash → { userId, family, expiresAt, usedAt }
  const sha256 = (v) => createHash("sha256").update(v).digest("hex");
  return {
    sha256,
    // 새 토큰 발급. family 를 주지 않으면 새 계열(새 로그인)
    async issue(userId, family = randomUUID()) {
      const token = randomBytes(32).toString("base64url");
      byHash.set(sha256(token), { userId, family, expiresAt: now() + ttlSec * 1000, usedAt: null });
      return token;
    },
    // '사용됨'으로 표시하고, 표시하기 전 상태를 돌려준다 (JS 는 단일 스레드라 이 함수 안은 원자적)
    async consume(hash) {
      const rec = byHash.get(hash);
      if (!rec) return null;
      const before = { ...rec };
      if (!rec.usedAt) rec.usedAt = now();
      return before;
    },
    async revokeFamily(family) {
      for (const [h, rec] of byHash) if (rec.family === family) byHash.delete(h);
    },
    async revokeUser(userId) {
      for (const [h, rec] of byHash) if (rec.userId === userId) byHash.delete(h);
    },
  };
}

// 로그인 실패 누적 (3-5). 계정 이름(정규화한 값) 기준, 5번째 실패부터 1·2·4…분, 최대 15분.
export function createLoginAttemptStore({ now = () => Date.now(), freeAttempts = 4, maxWaitMin = 15 } = {}) {
  const m = new Map(); // key → { failures, lockedUntil }
  return {
    async retryAfterSec(key) {
      const e = m.get(key);
      if (!e?.lockedUntil) return 0;
      const ms = e.lockedUntil - now();
      return ms > 0 ? Math.ceil(ms / 1000) : 0;
    },
    async fail(key) {
      const failures = (m.get(key)?.failures ?? 0) + 1;
      let lockedUntil = 0;
      if (failures > freeAttempts) {
        const minutes = Math.min(2 ** (failures - freeAttempts - 1), maxWaitMin);
        lockedUntil = now() + minutes * 60_000;
      }
      m.set(key, { failures, lockedUntil });
    },
    async reset(key) {
      m.delete(key);
    },
  };
}

// 패스키 (3-6). id(base64url) → { id, publicKey(Uint8Array), counter, userId, transports }
export function createPasskeyStore() {
  const m = new Map();
  return {
    async add(cred) {
      m.set(cred.id, { ...cred });
    },
    async findById(id) {
      return typeof id === "string" ? (m.get(id) ?? null) : null;
    },
    async listFor(userId) {
      return [...m.values()].filter((c) => c.userId === userId);
    },
    async updateCounter(id, counter) {
      const c = m.get(id);
      if (c) c.counter = counter;
    },
  };
}

// 키 → 값, 만료 포함 (웹훅 이벤트 ID, Idempotency-Key 에 사용)
export function createTtlMap(now = () => Date.now()) {
  const m = new Map();
  return {
    get(key) {
      const e = m.get(key);
      if (!e) return undefined;
      if (e.exp < now()) {
        m.delete(key);
        return undefined;
      }
      return e.value;
    },
    // 없을 때만 넣는다. 이미 있으면 false (원자적 "선점")
    setIfAbsent(key, value, ttlSec) {
      if (this.get(key) !== undefined) return false;
      m.set(key, { value, exp: now() + ttlSec * 1000 });
      return true;
    },
    set(key, value, ttlSec) {
      m.set(key, { value, exp: now() + ttlSec * 1000 });
    },
    delete(key) {
      m.delete(key);
    },
  };
}

export function createStores() {
  return {
    issued: createIssuedStore(),
    refreshTokens: createRefreshTokenStore(),
    loginAttempts: createLoginAttemptStore(),
    passkeys: createPasskeyStore(),
    webhookEvents: createTtlMap(),
    idempotency: createTtlMap(),
    settings: new Map(), // username → { theme, lang }
    payments: [], // 웹훅으로 반영된 결제 기록
    exports: createExportCounter(), // 5-5 관리자별 오늘 누적 내보내기 건수
  };
}
