// Express 5 앱 조립. Spring과 달리 보안 기본값이 없으니 하나씩 직접 붙인다 (0-5 수업).
import { randomBytes } from "node:crypto";
import path from "node:path";
import { fileURLToPath } from "node:url";
import cookieParser from "cookie-parser";
import cors from "cors";
import express from "express";
import rateLimit from "express-rate-limit";
import session from "express-session";
import helmet from "helmet";
import multer from "multer";
import { createLabRouter } from "./lab/index.js";
import { createAuth } from "./lib/auth.js";
import { accessLog } from "./lib/logger.js";
import { originCheck } from "./lib/origin-check.js";
import { accountRoutes } from "./routes/account.js";
import { addressRoutes } from "./routes/addresses.js";
import { authRoutes } from "./routes/auth.js";
import { avatarRoutes } from "./routes/avatars.js";
import { fileRoutes } from "./routes/files.js";
import { memberRoutes } from "./routes/members.js";
import { oidcRoutes } from "./routes/oidc.js";
import { orderRoutes } from "./routes/orders.js";
import { passkeyRoutes } from "./routes/passkey.js";
import { postViewRoutes } from "./routes/post-view.js";
import { postRoutes } from "./routes/posts.js";
import { previewRoutes } from "./routes/preview.js";
import { settingsRoutes } from "./routes/settings.js";
import { signupRoutes } from "./routes/signup.js";
import { tokenRoutes } from "./routes/token.js";
import { toolRoutes } from "./routes/tools.js";
import { userRoutes } from "./routes/users.js";
import { webhookRoutes } from "./routes/webhooks.js";

// 보안 구현은 /api 아래. 수업 화면의 짧은 경로(/posts, /login …)도 같은 라우터로 연결한다.
const PREFIXES = ["/api", "/"];
const VIEWS = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../views");

export function createApp(deps, { labMode = false } = {}) {
  const app = express();
  const auth = createAuth({
    keys: deps.keys, issued: deps.stores.issued, issuer: deps.config.jwtIssuer, signToken: deps.signToken,
    events: deps.securityEvents });

  // 4-4 로드밸런서 한 단계 뒤라면 1: 가장 가까운 1홉만 신뢰 (TRUST_PROXY, 기본 false = 프록시 없음)
  // true 로 두면 클라이언트가 보낸 X-Forwarded-For 맨 앞 값이 req.ip 가 되어 제한을 피해 간다
  app.set("trust proxy", deps.config.trustProxy);

  // 4-2 서버 렌더링 화면(EJS). <%= %> 는 자동 이스케이프
  app.set("views", VIEWS);
  app.set("view engine", "ejs");

  // 4-2 CSP: 요청마다 새 nonce. 이 nonce 가 붙은 <script> 만 실행된다
  app.use((req, res, next) => {
    res.locals.cspNonce =
      randomBytes(16).toString("base64");
    next(); });
  const nonce = (_, res) =>
    `'nonce-${res.locals.cspNonce}'`;
  app.use(helmet({ contentSecurityPolicy: {
    directives: {
      scriptSrc: ["'self'", nonce] } } }));

  // 4-5 CORS: 쿠키(자격 증명)를 쓰므로 출처를 정확히 (요청 Origin 반사·* 금지)
  app.use(cors({
    origin: deps.config.corsOrigins,
    credentials: true }));
  // 4-5 CSRF: 상태를 바꾸는 요청은 출처 확인 (CORS 는 CSRF 방어가 아니다)
  app.use(originCheck(deps.config.corsOrigins));

  // 4-4 서비스 전체 요청 제한. 키는 req.ip(위 trust proxy 설정으로 계산).
  // REDIS_URL 이 있으면 Redis 로 서버 간 카운터 공유, 없으면 서버별 메모리 (테스트·단일 서버)
  app.use(rateLimit({
    windowMs: 60_000, limit: deps.config.rateLimitPerMin,
    standardHeaders: "draft-8",
    legacyHeaders: false,
    store: deps.rateLimitStore,
  }));
  app.use(accessLog(deps.logger));

  app.get("/health", (req, res) => res.json({ status: "ok" }));

  // 웹훅은 원본 바디가 필요하므로 express.json 보다 먼저
  const webhooks = webhookRoutes(deps);
  for (const prefix of PREFIXES) app.use(prefix, webhooks);

  app.use(express.json({ limit: "100kb" }));
  // 입력은 각 라우트에서 zod 스키마로 검증

  // 3-2 리프레시 토큰 쿠키(rt) 읽기
  app.use(cookieParser());
  // 3-3 OIDC(state·nonce·PKCE)·3-6 패스키(챌린지)를 담는 서버 세션.
  // 값이 들어갈 때만 쿠키(sid)를 만든다. JWT API 인증에는 쓰지 않는다.
  app.use(session({
    name: "sid",
    secret: deps.config.sessionSecret,
    store: deps.sessionStore ?? new session.MemoryStore(), // 운영: Redis 등 공유 저장소
    resave: false,
    saveUninitialized: false,
    cookie: { httpOnly: true, secure: deps.config.cookieSecure,   // 화면: secure: true (여기선 auto → HTTP 실습도 동작)
      sameSite: "lax", maxAge: 30 * 60_000 },
  }));

  if (labMode) {
    app.use("/lab", createLabRouter(deps, auth));
    deps.logger.warn("LAB_MODE=vulnerable: /lab 아래에 취약한 실습 라우트가 등록되었습니다");
  }

  const api = express.Router();
  for (const make of [
    authRoutes, tokenRoutes, oidcRoutes, passkeyRoutes, postRoutes, postViewRoutes, orderRoutes, userRoutes,
    toolRoutes, fileRoutes, avatarRoutes, previewRoutes, settingsRoutes, accountRoutes,
    signupRoutes, memberRoutes, addressRoutes,
  ]) {
    api.use(make(deps, auth));
  }
  for (const prefix of PREFIXES) app.use(prefix, api);

  app.use((req, res) => res.status(404).json({ error: "not found" }));

  // 스택 트레이스·내부 메시지를 응답에 싣지 않는다
  app.use((err, req, res, next) => {
    let status = err.status ?? err.statusCode ?? 500;
    if (err instanceof multer.MulterError) status = err.code === "LIMIT_FILE_SIZE" ? 413 : 400;
    if (status >= 500) deps.logger.error("unhandled error", { path: req.path, err: err.message });
    const message = status >= 500 ? "internal error" : err.expose || err instanceof multer.MulterError ? err.message : "request rejected";
    res.status(status).json({ error: message });
  });

  return app;
}
