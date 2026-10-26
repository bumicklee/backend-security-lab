// 환경 변수 → 설정 객체. 값은 모두 실습용 가짜 값이다.
import path from "node:path";
import { fileURLToPath } from "node:url";

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const LOOPBACK = new Set(["127.0.0.1", "::1", "localhost"]);

export function loadConfig(env = process.env) {
  const isTest = env.NODE_ENV === "test";
  return {
    host: env.HOST ?? "127.0.0.1", // 기본값부터 로컬 전용
    port: Number(env.PORT ?? 3000),
    labMode: env.LAB_MODE === "vulnerable",
    dataBackend: env.DATA_BACKEND ?? (isTest ? "memory" : "db"), // memory | db
    databaseUrl:
      env.DATABASE_URL ?? "postgresql://lab:lab-only-password@localhost:5432/express_board",
    mongoUrl: env.MONGO_URL ?? "mongodb://localhost:27017",
    mongoDb: env.MONGO_DB ?? "express_lab",
    webhookSecret: env.WEBHOOK_SECRET ?? "whsec_lab_only_example",
    webhookToleranceSec: Number(env.WEBHOOK_TOLERANCE_SEC ?? 300),
    jwtIssuer: env.JWT_ISSUER ?? "https://api.example.com",
    // 3-2·3-3·3-6 세션 쿠키(sid) 서명 키. 실습용 가짜 값
    sessionSecret: env.SESSION_SECRET ?? "session-secret-EXAMPLE-only",
    // Secure 쿠키: auto = HTTPS(또는 프록시가 알려 준 HTTPS) 요청일 때만 Secure. true 로 고정 권장(운영)
    cookieSecure: env.COOKIE_SECURE === "true" ? true : env.COOKIE_SECURE === "false" ? false : "auto",
    // 3-3 OIDC: IdP 가 없으면 /login/idp 는 503
    oidcIssuer: env.OIDC_ISSUER ?? "",
    oidcClientId: env.OIDC_CLIENT_ID ?? "board-web",
    oidcClientSecret: env.IDP_SECRET ?? "",
    oidcRedirectUri: env.OIDC_REDIRECT_URI ?? "https://board.example.com/callback",
    // 3-6 패스키: RP ID·오리진은 고정값으로 검증한다
    webauthnRpId: env.WEBAUTHN_RP_ID ?? "localhost",
    webauthnOrigin: env.WEBAUTHN_ORIGIN ?? "http://localhost:3000",
    labJwtSecret: env.LAB_JWT_SECRET ?? "change-me", // 취약 실습용: 유출된 HS256 비밀키 역할
    filesDir: path.resolve(env.FILES_DIR ?? path.join(ROOT, "data/files")),
    storeDir: path.resolve(env.STORE_DIR ?? path.join(ROOT, "data/avatars")),
    labPublicDir: path.resolve(env.LAB_PUBLIC_DIR ?? path.join(ROOT, "data/lab-public")),
    previewAllowedHosts: (env.PREVIEW_ALLOWED_HOSTS ?? "news.example.com,blog.example.com")
      .split(",")
      .map((h) => h.trim())
      .filter(Boolean),
    rateLimitPerMin: Number(env.RATE_LIMIT_PER_MIN ?? 100),
    // 4-4 프록시 신뢰: 숫자(홉 수) · true/false · IP/서브넷 목록. 실제로 거치는 프록시만큼만.
    // 기본 false: 앞에 프록시가 없는 로컬·compose 실행에서 1 로 두면 클라이언트가 보낸 X-Forwarded-For 를
    // 그대로 믿게 된다. 로드밸런서 한 단계 뒤에 배포하면 TRUST_PROXY=1 (수업 화면의 설정)
    trustProxy: parseTrustProxy(env.TRUST_PROXY ?? "false"),
    // 4-4 요청 제한 카운터 공유 저장소(Redis). 비어 있으면 서버별 메모리. 테스트에서는 항상 메모리.
    // 셸에 흔히 잡혀 있는 REDIS_URL 과 섞이지 않도록 전용 이름을 쓴다
    redisUrl: isTest ? "" : (env.RATE_LIMIT_REDIS_URL ?? ""),
    // 4-5 CORS·CSRF 출처 허용 목록 (쉼표 구분)
    corsOrigins: (env.CORS_ORIGINS ?? "https://app.example.com")
      .split(",")
      .map((o) => o.trim())
      .filter(Boolean),
    logLevel: env.LOG_LEVEL ?? (isTest ? "silent" : "info"),
    // 5-1 내부 API: 사내 CA 로만 검증하는 클라이언트. 둘 다 있을 때만 만든다 (테스트는 네트워크 없음)
    internalApiUrl: env.INTERNAL_API_URL ?? "",
    internalCaFile: env.INTERNAL_CA_FILE ?? "",
    // 5-2 봉투 암호화: KMS 키 ARN/별칭. 비우면 프로세스 안의 가짜 KMS(lib/local-kms.js, 실습 전용)
    kmsKeyId: env.KMS_KEY_ID ?? "",
    // 5-2 블라인드 인덱스 키: 암호화 키와 따로. 운영은 시크릿 매니저에서 (아래 기본값은 실습용 가짜 값)
    phoneIndexKey: env.PHONE_INDEX_KEY ?? "phone-index-key-EXAMPLE-only-change-me",
    // 5-3 시크릿 소스: env(기본, 환경 변수) | aws(Secrets Manager, prod/board-api/db · …/webhook)
    secretsSource: env.SECRETS_SOURCE ?? "env",
    secretsPrefix: env.SECRETS_PREFIX ?? "prod/board-api",
    // 5-4 개인정보 접속기록 파일(append only). 테스트·비워 두면 메모리
    privacyAuditFile: isTest ? "" : (env.PRIVACY_AUDIT_FILE ?? path.join(ROOT, "data/audit/privacy-access.jsonl")),
    // 5-4 파기 배치 보존 기간(일): 탈퇴 회원 / 주문 구매자(다른 법이 정한 기간)
    memberRetentionDays: Number(env.MEMBER_RETENTION_DAYS ?? 30),
    orderRetentionDays: Number(env.ORDER_RETENTION_DAYS ?? 5 * 365),
    // 5-5 관리자 내보내기: 오늘 누적 건수가 이 값을 넘으면 알림
    exportAlertThreshold: Number(env.EXPORT_ALERT_THRESHOLD ?? 10_000),
    // 5-7 JWT 를 KMS 비대칭 키(RSA_2048, SIGN_VERIFY)로 서명. 비우면 기존 방식(프로세스 RS256 키)
    jwtKmsKeyId: env.JWT_KMS_KEY_ID ?? "",
    // 6-2 보안 이벤트 로그(한 줄에 JSON, "ts" 필드). fail2ban 이 읽는다. 테스트·비워 두면 메모리
    securityLogFile: isTest ? "" : (env.SECURITY_LOG_FILE ?? path.join(ROOT, "data/log/security.log")),
  };
}

export function parseTrustProxy(v) {
  if (v === "true") return true;
  if (v === "false") return false;
  return /^\d+$/.test(v) ? Number(v) : v;
}

// 취약 실습 모드는 루프백 주소에서만 허용한다. 0.0.0.0이면 시작을 거부.
export function assertSafeBind({ labMode, host }) {
  if (labMode && !LOOPBACK.has(host)) {
    throw new Error(
      `LAB_MODE=vulnerable 은 HOST=127.0.0.1 에서만 실행할 수 있습니다 (현재 HOST=${host}). ` +
        "취약한 실습 코드를 네트워크에 열지 마세요.",
    );
  }
}

export function isLoopbackHost(host) {
  return LOOPBACK.has(host);
}
