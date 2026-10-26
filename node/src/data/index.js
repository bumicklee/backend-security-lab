// 데이터 계층 조립. 라우트는 deps 객체만 바라보므로 테스트에서 대역으로 바꿔 끼울 수 있다.
import { execFile } from "node:child_process";
import { lookup } from "node:dns/promises";
import { promisify } from "node:util";
import { createHash } from "node:crypto";
import { KMSClient } from "@aws-sdk/client-kms";
import { createFileAuditLog, createMemoryAuditLog } from "../lib/audit-log.js";
import { loadKeys } from "../lib/auth.js";
import { createFieldCrypto } from "../lib/envelope.js";
import { logAlert } from "../lib/export-guard.js";
import { createKmsJwtSigner, kmsPublicKeyPem } from "../lib/kms-jwt.js";
import { createLocalKms } from "../lib/local-kms.js";
import { createLogger } from "../lib/logger.js";
import { createMemoryCounter } from "../lib/lookup-counter.js";
import { createSecurityEvents } from "../lib/security-events.js";
import { createInternalApi } from "../clients/internal-api.js";
import { createMemberRepo, seedMembers } from "./members.js";
import { createStores } from "./stores.js";

export async function createDeps(config, overrides = {}) {
  let prisma, mongo;
  if (config.dataBackend === "memory") {
    const m = await import("./memory.js");
    prisma = await m.createMemoryPrisma();
    mongo = await m.createMemoryMongo();
  } else {
    const d = await import("./db.js");
    prisma = await d.createDbPrisma(config.databaseUrl);
    mongo = await d.createDbMongo(config.mongoUrl, config.mongoDb);
  }
  // 4-4 REDIS_URL 이 있을 때만 Redis 저장소 (서버 여러 대가 카운터를 공유)
  let redis, rateLimitStore;
  if (config.redisUrl) {
    const { createClient } = await import("redis");
    const { RedisStore } = await import("rate-limit-redis");
    redis = createClient({ url: config.redisUrl });
    await redis.connect();
    rateLimitStore = new RedisStore({ sendCommand:
      (...args) => redis.sendCommand(args) });
  }
  const logger = overrides.logger ?? createLogger(config.logLevel);
  const stores = overrides.stores ?? createStores();

  // 5-2·5-7 KMS: 키 ID 가 있으면 AWS KMS(서버 역할 권한), 없으면 프로세스 안의 가짜 KMS(실습 전용)
  if (config.jwtKmsKeyId && !config.kmsKeyId && !overrides.kms) {
    throw new Error("JWT_KMS_KEY_ID 를 쓰려면 KMS_KEY_ID(데이터 키용)도 지정하세요");
  }
  const useAwsKms = Boolean(config.kmsKeyId || config.jwtKmsKeyId);
  const kms = overrides.kms ?? (useAwsKms ? new KMSClient({}) : createLocalKms({
    masterKey: createHash("sha256").update("local-kms-master-EXAMPLE-only").digest() }));
  if (!useAwsKms && config.logLevel !== "silent" && !overrides.kms) {
    logger.warn("KMS_KEY_ID 가 없어 가짜 KMS(lib/local-kms.js)로 암호화합니다. 실습 전용");
  }
  const fieldCrypto = createFieldCrypto({
    kms, keyId: config.kmsKeyId || "alias/board-local", indexKey: Buffer.from(config.phoneIndexKey) });
  const members = createMemberRepo(prisma, fieldCrypto);
  await seedMembers(prisma, members);

  // 5-7 JWT 서명: JWT_KMS_KEY_ID 가 있으면 KMS Sign + 발급 기록, 공개키는 KMS 에서 받는다
  let keys, signToken;
  if (config.jwtKmsKeyId) {
    keys = { privateKey: null, publicKey: await kmsPublicKeyPem(kms, config.jwtKmsKeyId) };
    signToken = createKmsJwtSigner({ kms, keyId: config.jwtKmsKeyId, issued: stores.issued });
  } else {
    keys = loadKeys();
  }

  return {
    config,
    rateLimitStore,
    prisma,
    users: mongo.users,
    labUsers: mongo.labUsers,
    stores,
    keys,
    signToken,
    kms,
    fieldCrypto,
    members,
    // 5-4 개인정보 접속기록 (일반 로그와 분리, append only)
    auditLog: config.privacyAuditFile ? createFileAuditLog(config.privacyAuditFile) : createMemoryAuditLog(),
    // 6-2 계정별 조회 수(Redis 가 있으면 Redis, 없으면 메모리)와 보안 이벤트 로그(security.log)
    lookupCounter: redis ?? createMemoryCounter(),
    securityEvents: createSecurityEvents({ file: config.securityLogFile, logger }),
    // 5-5 반출 알림 (기본: 경고 로그)
    alert: logAlert(logger),
    // 5-1 내부 API (사내 CA 만 신뢰). 설정이 없으면 null
    internalApi: config.internalApiUrl && config.internalCaFile
      ? createInternalApi({ baseUrl: config.internalApiUrl, caFile: config.internalCaFile }) : null,
    logger,
    // 외부 효과(명령 실행, DNS, HTTP)도 주입받는다 → 테스트에서 가짜로 교체
    run: promisify(execFile),
    lookup,
    fetch: globalThis.fetch,
    async close() {
      await prisma.$disconnect?.();
      await mongo.close?.();
      await redis?.quit();
    },
    ...overrides,
  };
}
