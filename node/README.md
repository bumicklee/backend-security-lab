# Express 5 예제 서비스 (node/)

「백엔드 개발자를 위한 서버 보안 실무」 강의의 Express 쪽 예제 서비스입니다.
게시판 · 파일 업로드 · 결제 웹훅 · 모바일 앱 연동 API(주문 + Idempotency-Key)를 담은 작은 서비스이고,
같은 기능을 `../spring/` 에서 Spring Boot 4 로도 만들어 두었습니다.

> ⚠️ **안전 고지**
> - 이 저장소에는 **일부러 넣은 취약한 코드**(`src/lab/`)가 있습니다. 내 PC나 실습용 가상 서버에서만 실행하세요.
> - 취약 라우트(`/lab/...`)는 `LAB_MODE=vulnerable` **이고** `HOST=127.0.0.1` 일 때만 등록됩니다. `0.0.0.0` 이면 서버가 시작을 거부합니다.
> - 인터넷·회사망에 열지 마세요. 클라우드에 올린다면 방화벽으로 내 IP만 허용합니다.
> - 실습 결과로 얻은 기법을 허락받지 않은 시스템에 쓰는 것은 불법입니다.
> - 계정·비밀번호·키·카드번호는 모두 가짜 값입니다(`example.com`, `203.0.113.x`, `EXAMPLE`).

## 구성

| 항목 | 내용 |
|---|---|
| 런타임 | Node.js 22 이상 (ESM), 권장 Node.js 24 LTS — Docker 이미지는 6-5 부터 멀티 스테이지(`node:24-slim` 빌드 → `gcr.io/distroless/nodejs24-debian12:nonroot` 실행, 다이제스트 고정) |
| 웹 | Express 5.3, helmet 8, express-rate-limit 8, zod 4, multer 2, jsonwebtoken 9 |
| 인증 | argon2(Argon2id), bcryptjs(예전 해시 검증), cookie-parser, express-session, openid-client 6, @simplewebauthn/server 14 |
| 게시판·주문 DB | PostgreSQL + Prisma 7 (`@prisma/adapter-pg`) |
| 로그인 계정 DB | MongoDB (NoSQL 인젝션 실습용 `users`, `lab_users` 컬렉션) |
| 섹션 5 | `@aws-sdk/client-kms`(봉투 암호화·JWT 서명), `@aws-sdk/client-secrets-manager`(시크릿 로더) — 키 ID 가 없으면 프로세스 안의 가짜 KMS |
| 테스트 | Jest 30 + supertest 7 (DB 없이 메모리 대역으로 실행) |

보안 구현은 `/api/...` 아래에 있고, 수업 화면과 같은 짧은 경로(`/posts`, `/login`, `/files` …)로도 똑같이 호출됩니다.
취약한 구현은 실습 모드에서만 `/lab/...` 아래에 생깁니다.

## 실행

### 1) Docker Compose (수업 0-5와 같은 방법)

상위 폴더(`backend-security-lab/`)에서 DB와 두 서버를 한 번에 띄웁니다.

```bash
docker compose up -d
curl -s localhost:8080/health; echo; curl -s localhost:3000/health
# {"status":"UP"}
# {"status":"ok"}
```

컨테이너 안의 서버는 `HOST=0.0.0.0` 이어야 포트 연결이 되므로 **Compose 로는 보안 구현(/api)만 뜹니다.**
취약 실습은 아래 2)·3)처럼 가상 서버(또는 내 PC)에서 직접 실행하세요.

### 2) DB 없이 바로 실행 (메모리 모드)

```bash
npm ci
npm run dev        # 보안 구현만, http://127.0.0.1:3000
npm run dev:lab    # + 취약 실습 라우트(/lab), 127.0.0.1 전용
```

메모리 모드는 PostgreSQL 대신 pg-mem(메모리 PostgreSQL 에뮬레이터), MongoDB 대신 작은 메모리 컬렉션을 씁니다.
SQL 인젝션 실습 결과도 실제 PostgreSQL 과 같은 모양으로 재현됩니다. 재시작하면 데이터는 초기화됩니다.

### 3) 실제 DB 와 함께 실습 모드

Compose 의 DB 포트를 `127.0.0.1` 에만 연 상태에서:

```bash
cp .env.example .env   # 필요하면 값 수정
npx prisma db push     # 테이블 생성 (DB 가 없으면 만든다)
HOST=127.0.0.1 LAB_MODE=vulnerable \
DATABASE_URL=postgresql://lab:lab-only-password@localhost:5432/express_board \
MONGO_URL=mongodb://localhost:27017 node src/server.js
```

다른 PC 의 프록시 도구(Burp·ZAP)에서 가상 서버의 실습 모드에 접근하려면 포트를 열지 말고 SSH 터널을 씁니다.

```bash
ssh -L 3000:127.0.0.1:3000 admin@198.51.100.10   # 이후 내 PC 에서 localhost:3000
```

## 환경 변수

| 변수 | 기본값 | 설명 |
|---|---|---|
| `HOST` | `127.0.0.1` | 바인딩 주소. Docker 이미지는 `0.0.0.0` |
| `PORT` | `3000` | |
| `LAB_MODE` | (없음) | `vulnerable` 이면 `/lab` 취약 라우트 등록. `HOST` 가 루프백이 아니면 시작 거부 |
| `DATA_BACKEND` | `db` (테스트는 `memory`) | `db` = PostgreSQL + MongoDB, `memory` = DB 없이 실행 |
| `DATABASE_URL` | `postgresql://lab:lab-only-password@localhost:5432/express_board` | Prisma 접속 주소 |
| `MONGO_URL` | `mongodb://localhost:27017` | |
| `MONGO_DB` | `express_lab` | |
| `WEBHOOK_SECRET` | `whsec_lab_only_example` | 결제 웹훅 HMAC 비밀값 (가짜) |
| `WEBHOOK_TOLERANCE_SEC` | `300` | 웹훅 타임스탬프 허용 범위 |
| `PREVIEW_ALLOWED_HOSTS` | `news.example.com,blog.example.com` | 링크 미리보기 허용 호스트 |
| `RATE_LIMIT_PER_MIN` | `100` | IP(`req.ip`) 당 분당 요청 수 |
| `TRUST_PROXY` | `false` | 4-4 Express `trust proxy`. `false`(앞에 프록시 없음) · `1`(로드밸런서 한 단계 뒤, 수업 화면) · IP/서브넷 목록. `true` 는 쓰지 않는다 |
| `RATE_LIMIT_REDIS_URL` | (없음) | 4-4 요청 제한 카운터를 Redis 에 공유(`rate-limit-redis`). 없으면 서버별 메모리, 테스트는 항상 메모리. compose 는 `redis://redis:6379` |
| `CORS_ORIGINS` | `https://app.example.com` | 4-5 CORS 허용 출처이자 CSRF 출처 확인 허용 목록 (쉼표 구분) |
| `FILES_DIR` / `STORE_DIR` | `data/files` / `data/avatars` | 첨부 파일 폴더 / 업로드 저장 폴더(웹 루트 밖) |
| `LAB_JWT_SECRET` | `change-me` | 취약 실습에서 "유출된 HS256 비밀키" 역할 |
| `JWT_ISSUER` | `https://api.example.com` | 발급·검증 때 확인하는 `iss` (3-2) |
| `SESSION_SECRET` | `session-secret-EXAMPLE-only` | 세션 쿠키(`sid`) 서명 키 (가짜 값) |
| `COOKIE_SECURE` | (auto) | `true`·`false`. 기본 auto = HTTPS 요청일 때만 세션 쿠키에 `Secure` |
| `OIDC_ISSUER` / `OIDC_CLIENT_ID` / `IDP_SECRET` | (없음) / `board-web` / (없음) | 3-3 IdP. `OIDC_ISSUER` 가 없으면 `/login/idp` 는 503 |
| `OIDC_REDIRECT_URI` | `https://board.example.com/callback` | IdP 에 등록한 값과 글자 그대로 |
| `WEBAUTHN_RP_ID` / `WEBAUTHN_ORIGIN` | `localhost` / `http://localhost:3000` | 3-6 패스키 검증에 고정으로 쓰는 RP ID·오리진 |
| `JWT_PRIVATE_KEY_FILE`, `JWT_PUBLIC_KEY_FILE` | (없음) | 지정하지 않으면 시작할 때마다 RS256 키 쌍을 새로 만든다 |
| `LOG_LEVEL` | `info` | `debug`·`info`·`warn`·`error`·`silent` |
| `INTERNAL_API_URL` / `INTERNAL_CA_FILE` | (없음) | 5-1 내부 API 클라이언트(`src/clients/internal-api.js`). 둘 다 있을 때만 만들고, 이 클라이언트는 사내 CA 만 믿는다 |
| `KMS_KEY_ID` | (없음) | 5-2 봉투 암호화 KMS 키 ARN·별칭. 없으면 가짜 KMS(`src/lib/local-kms.js`, 실습 전용). 리전·자격 증명은 AWS SDK 기본 체인(`AWS_REGION`, 서버 역할) |
| `PHONE_INDEX_KEY` | `phone-index-key-EXAMPLE-only-change-me` | 5-2 블라인드 인덱스 HMAC 키 (32바이트 이상, 암호화 키와 따로) |
| `SECRETS_SOURCE` / `SECRETS_PREFIX` | `env` / `prod/board-api` | 5-3 `aws` 면 시작할 때 Secrets Manager 에서 `<prefix>/db`(`{"url": …}`)·`<prefix>/webhook`(`{"secret": …}`)을 읽는다 |
| `PRIVACY_AUDIT_FILE` | `data/audit/privacy-access.jsonl` (테스트는 메모리) | 5-4 개인정보 접속기록(한 줄에 JSON 하나, append only) |
| `MEMBER_RETENTION_DAYS` / `ORDER_RETENTION_DAYS` | `30` / `1825` | 5-4 파기 배치(매일 03:30): 탈퇴 회원 삭제 / 주문 구매자 익명화 기준 |
| `EXPORT_ALERT_THRESHOLD` | `10000` | 5-5 관리자별 오늘 누적 내보내기 건수 알림 기준 |
| `JWT_KMS_KEY_ID` | (없음) | 5-7 로그인 토큰을 KMS 비대칭 키(RSA, SIGN_VERIFY)로 서명. `KMS_KEY_ID` 도 필요 |
| `SECURITY_LOG_FILE` | `data/log/security.log` (테스트는 메모리) | 6-2 보안 이벤트 로그(한 줄에 JSON, 첫 필드 `ts`). `bulk_lookup`·`bulk_block`·`authz_denied` 를 남기고 fail2ban(`../ops/fail2ban/`)이 읽는다 |

## 실습 계정 (가짜)

| 아이디 | 비밀번호 | 역할 |
|---|---|---|
| `admin` | `admin-lab-pass` | ADMIN |
| `alice` | `alice-lab-pass` | USER |
| `bob` | `bob-lab-pass` | USER |

```bash
TOKEN=$(curl -s localhost:3000/api/login -H "Content-Type: application/json" \
  -d '{"username":"alice","password":"alice-lab-pass"}' | jq -r .token)
curl -s localhost:3000/api/me -H "Authorization: Bearer $TOKEN"
```

시드 계정의 비밀번호 해시는 일부러 예전 형식(`scrypt$…`)으로 넣어 둡니다. 한 번 로그인하면 `$argon2id$v=19$m=19456,p=1,t=2$…`로 바뀝니다(3-1).
node-argon2 는 파라미터를 `m,p,t` 순서로 적습니다(Spring 의 Bouncy Castle 은 `m,t,p`).

주문 1000~1010 이 시드로 들어 있습니다. 1001 은 alice(24,000원), 1002 는 bob(30,000원) 것입니다(3-4).
이미 만든 DB 에서 시작했다면 `docker compose down -v` 로 데이터를 지우고 다시 띄워야 시드가 들어갑니다.

## 수업 ↔ 코드 지도

| 수업 | 보안 구현 (`/api`) | 취약 구현 (`/lab`, 실습 모드) | 테스트 |
|---|---|---|---|
| 0-2 서명만 검증 vs 발급 기록 확인 | `src/lib/auth.js` · `GET /me`, `POST /login`, `POST /logout` | `src/lab/login.js` · `GET /lab/me` (HS256 `change-me`) | `tests/auth.jwt.test.js` |
| 0-3 로그 마스킹 | `src/lib/mask.js`, `src/lib/logger.js` (웹훅 카드번호 마스킹) | — | `tests/mask.test.js` |
| 0-5 Express 보안 기본 세트 | `src/create-app.js` (helmet · rateLimit · `express.json({ limit: "100kb" })` · zod), `GET /health` | `LAB_MODE` 바인딩 검사 `src/config.js` | `tests/lab-mode.test.js` |
| 2-2 입력 검증 | `src/routes/orders.js` · `POST /orders`, `src/routes/users.js` · `GET /users?name=` | `src/lab/orders.js`, `src/lab/users.js` | `tests/orders.validation.test.js` |
| 2-3 SQL·NoSQL 인젝션 | `src/routes/posts.js` · `GET /posts?title=` (`$queryRaw` 태그드 템플릿), `src/routes/auth.js` · `POST /login` (zod 문자열) | `src/lab/posts.js` (`$queryRawUnsafe`), `src/lab/login.js` (`findOne(req.body)`) | `tests/injection.test.js` |
| 2-4 커맨드 인젝션·경로 조작 | `src/routes/tools.js` · `GET /admin/ping?host=` (execFile + `--`), `src/routes/files.js` · `GET /files?name=` (sendFile `root`) | `src/lab/tools.js` (exec), `src/lab/files.js` (path.join) | `tests/command-path.test.js` |
| 2-5 파일 업로드 | `src/routes/avatars.js` · `POST /avatars`, `GET /avatars/:id` (매직 바이트, UUID 이름, attachment + nosniff) | `src/lab/avatars.js` (MIME 헤더만 확인, 원본 이름, `/lab/uploads` 정적 서빙) | `tests/avatars.test.js` |
| 2-6 SSRF | `src/routes/preview.js` · `GET /preview?url=` (https + 허용 목록, 해석 IP 검사, `redirect: "error"`), `src/lib/net.js` | `src/lab/preview.js` (그대로 fetch) + 루프백 전용 가짜 메타데이터 `/lab/fake-metadata/...` | `tests/preview.ssrf.test.js` |
| 2-7 프로토타입 오염 | `src/routes/settings.js` · `PATCH /me/settings` (zod `z.strictObject({...}).partial()`), `src/lib/safe-merge.js` | `src/lab/settings.js` (깊은 병합) · `GET /lab/admin/users`, 정리용 `POST /lab/reset-prototype` | `tests/prototype-pollution.test.js` |
| 2-8 공격 재현 테스트 | 위 테스트 전체 | — | `tests/attack.test.js` (수업 화면 코드) |
| 3-1 비밀번호 저장 | `src/lib/password.js` · `verifyAndUpgrade` (Argon2id m=19456·t=2·p=1, `scrypt$`·bcrypt 예전 해시 → 로그인 때 이전), `POST /login` | `lab_users` 컬렉션(평문 저장, 나쁜 예) | `tests/password.test.js` |
| 3-2 세션 vs JWT | `src/lib/auth.js` (RS256 고정 + `issuer` + jti 발급 기록), `src/routes/token.js` · `POST /token/refresh` (리프레시 토큰 회전 + 재사용 시 계열 폐기, `rt` HttpOnly 쿠키), `src/data/stores.js` · `createRefreshTokenStore` | `GET /lab/me` (서명만 확인) | `tests/refresh.test.js`, `tests/auth.jwt.test.js` |
| 3-3 OAuth 2.0·OIDC | `src/routes/oidc.js` · `GET /login/idp`(=`/login/oidc`), `GET /callback` (PKCE S256 · state · nonce, 세션에 한 번만, `iss`+`sub` 식별) | — | `tests/oidc.test.js` (가짜 IdP) |
| 3-4 IDOR·BOLA | `src/routes/orders.js` · `GET /orders/:id` (주인 조건 쿼리, 남의·없는 주문 403, 숫자 아님 400), `GET /admin/users` (ADMIN), `PUT/DELETE /posts/:id` 작성자 확인 | `src/lab/orders.js` · `GET /lab/orders/:id` (로그인만 확인) | `tests/orders.authz.test.js`, `tests/orders.idor.test.js` |
| 3-5 무차별 대입 | `src/routes/auth.js` · `POST /login` (소문자 정규화 계정 키로 실패 누적, 5번째 실패부터 1·2·4…분·최대 15분, 429 + `Retry-After`), `createLoginAttemptStore` | — | `tests/login.bruteforce.test.js` |
| 3-6 MFA·세션 탈취 | `src/routes/passkey.js` · `POST /webauthn/register/options`·`/finish`, `POST /webauthn/login/options`·`/finish`(204), `POST /me/sessions/revoke-all` (`issued.deleteAllFor` + `refreshTokens.revokeUser`) | — | `tests/passkey.test.js` |
| 3-7 인증·인가 테스트 | 인가 매트릭스(4행 + 토큰 없음 401) | — | `tests/orders.authz.test.js` |
| 4-1 Mass assignment·과도한 노출 | `src/routes/users.js` · `PATCH /users/me` (`z.strictObject` 허용 목록 + `view` 응답), 주문 금액은 서버 계산 | `src/lab/profile.js` · `PATCH /lab/users/me` (`$set: req.body`, 문서 통째 응답) | `tests/profile.test.js`, `tests/price-tamper.test.js`, `tests/lab-section4.test.js` |
| 4-2 XSS·CSP | `src/routes/post-view.js` · `GET /posts/:id/view` (`views/post.ejs`, `<%= %>`), `GET /posts/:id/rich` (`src/lib/sanitize-html.js` DOMPurify + jsdom), `src/create-app.js` helmet CSP 요청별 nonce | `src/lab/post-view.js` · `GET /lab/posts/:id/view` (템플릿 문자열, CSP 제거) | `tests/xss.test.js`, `tests/lab-section4.test.js` |
| 4-3 웹훅 서명·재전송 | `src/routes/webhooks.js` · `POST /webhooks/payment` (`express.raw` + HMAC + 타임스탬프 + 이벤트 ID, 깨진 JSON 400) | `src/lab/webhooks.js` (서명 확인 없음) | `tests/webhook.test.js` |
| 4-4 Rate limiting·멱등성 키 | `src/create-app.js` (`trust proxy` = `TRUST_PROXY`, express-rate-limit 8 + `RedisStore`), `src/lib/idempotency.js` · `POST /orders` 의 `Idempotency-Key` | — | `tests/rate-limit.test.js`, `tests/orders.validation.test.js` |
| 4-5 CORS·CSRF | `src/create-app.js` (`cors` 허용 목록 + `credentials`), `src/lib/origin-check.js` (Sec-Fetch-Site·Origin 확인), `src/routes/account.js` · `POST /account/email` (세션 쿠키 인증) | — | `tests/csrf.test.js` |
| 5-1 TLS·인증서 검증 | `src/clients/internal-api.js` (`new https.Agent({ ca })`, https 만, `INTERNAL_API_URL`·`INTERNAL_CA_FILE`) | — | `tests/tls.internal-api.test.js` (사내 CA 지정, 검증 끄기 코드 없음) |
| 5-2 저장 데이터 암호화·KMS | `src/lib/envelope.js` (`GenerateDataKeyCommand` + `aes-256-gcm` 12바이트 IV·태그 + HMAC 블라인드 인덱스), `src/lib/local-kms.js`(가짜 KMS), `src/data/members.js`, `GET /admin/members?phone=` | — | `tests/envelope.test.js` |
| 5-3 시크릿 관리 | `src/lib/secrets.js` (`GetSecretValueCommand`, 용도별 이름, 5분 캐시), `src/server.js` 의 `SECRETS_SOURCE=aws` · 최상위 `.gitleaks.toml`·`.pre-commit-config.yaml`·`.github/workflows/secret-scan.yml` | — | `tests/secrets.test.js` |
| 5-4 개인정보 | `src/lib/mask.js`, `POST /signup`(`z.strictObject`, 최소 수집), `src/lib/privacy-access.js`(접속기록 다섯 항목) + `src/lib/audit-log.js`(append only), `GET /admin/members/:id`, `src/jobs/purge-expired.js`(`purgeExpired`, 매일 03:30), `POST /orders` 고른 필드 로그 | — | `tests/mask.test.js`, `tests/privacy.test.js` |
| 5-5 반출 감시 | `src/routes/members.js` · `GET /admin/members/export?page=&fields=` (`orderBy: { id: "asc" }`, `take: 1000`, 컬럼 허용 목록, `addExport` 누적 → `alert("bulk-export")`), `src/lib/export-guard.js` | — | `tests/export.test.js` |
| 6-2 계정별 조회량 감시 | `src/middleware/bulk-lookup.js` · `bulkLookupGuard(redis, events)` (키 `lookup:<계정>:<날짜>`, 300번째 `bulk_lookup`, 1001번째 `bulk_block`, 1000 초과 429 `review_required`), `src/routes/addresses.js` · `GET /addresses`, `src/lib/security-events.js`(security.log), `src/lib/lookup-counter.js`(Redis 없을 때 메모리), `requireRole` 403 → `authz_denied` | — | `tests/bulk-lookup.test.js` |
| 6-5 컨테이너 | `Dockerfile` (멀티 스테이지, distroless nonroot 65532, 셸 없음), `scripts/start.js`(셸 없이 `prisma db push` → 서버), 최상위 `compose.yaml`(read_only · tmpfs · cap_drop · no-new-privileges) | — | `docker compose up --wait` |
| 5-7 KMS 서명·발급 기록 | `src/lib/kms-jwt.js` (`SignCommand` `RSASSA_PKCS1_V1_5_SHA_256` + `issued.save(jti, exp)`, 공개키는 `GetPublicKeyCommand`), `JWT_KMS_KEY_ID` · 최상위 `ops/sql/find_unissued_tokens.sql` | — | `tests/kms-jwt.test.js` (새 서명키로 위조해도 발급 기록이 없으면 401) |

### 수업에서 쓰는 요청 예시

```bash
# 2-3 SQL 인젝션: 보안 구현은 0건, 실습 모드는 전체/회원 정보가 나온다
curl -sG localhost:3000/posts --data-urlencode "title=' OR 1=1--"
curl -sG localhost:3000/lab/posts --data-urlencode "title=' UNION SELECT id, email, password_hash FROM users--"

# 2-3 NoSQL 인젝션
curl -s localhost:3000/lab/login -H "Content-Type: application/json" \
  -d '{"username":"admin","password":{"$ne":null}}'

# 2-6 SSRF: 실습 모드에서 서버가 "내부" 메타데이터를 대신 가져온다 (가짜 값)
M=http://127.0.0.1:3000/lab/fake-metadata/latest/meta-data
curl -sG localhost:3000/lab/preview --data-urlencode "url=$M/iam/security-credentials/app-role"

# 2-7 프로토타입 오염 후 정리
curl -s -X PATCH localhost:3000/lab/me/settings -H "Content-Type: application/json" -d '{"__proto__":{"isAdmin":true}}'
curl -s localhost:3000/lab/admin/users
curl -s -X POST localhost:3000/lab/reset-prototype

# 3-4 IDOR: 보안 구현은 남의 주문 403, 실습 모드는 1000~1010 이 모두 200
TOKEN=$(curl -s localhost:3000/api/login --json '{"username":"alice","password":"alice-lab-pass"}' | jq -r .token)
curl -si localhost:3000/api/orders/1002 -H "Authorization: Bearer $TOKEN" | head -1     # HTTP/1.1 403 Forbidden (화면 s4 마지막 줄)
LAB=$(curl -s localhost:3000/lab/login -H "Content-Type: application/json" \
  -d '{"username":"alice","password":"alice-lab-pass"}' | jq -r .token)
for id in $(seq 1000 1010); do curl -s -o /dev/null -w "%{http_code} " -H "Authorization: Bearer $LAB" localhost:3000/lab/orders/$id; done; echo

# 3-5 무차별 대입: 401 다섯 번 뒤 429 + Retry-After: 60
for i in 1 2 3 4 5 6; do curl -s -o /dev/null -w "%{http_code} " localhost:3000/login \
  -H "Content-Type: application/json" -d '{"username":"bob","password":"wrong"}'; done; echo

# 3-2 리프레시 토큰 회전 (쿠키 rt 는 Secure 라 HTTPS 가 아니면 curl -c 에 저장되지 않을 수 있어 직접 꺼낸다)
RT=$(curl -s -D - -o /dev/null localhost:3000/login -H "Content-Type: application/json" \
  -d '{"username":"alice","password":"alice-lab-pass"}' | sed -n 's/^[Ss]et-[Cc]ookie: rt=\([^;]*\).*/\1/p')
curl -s -X POST localhost:3000/token/refresh -H "Cookie: rt=$RT" | jq .ok      # true
curl -s -o /dev/null -w "%{http_code}\n" -X POST localhost:3000/token/refresh -H "Cookie: rt=$RT"   # 401 (재사용)

# 4-1 mass assignment: 보안 구현은 400, 실습 모드는 role 이 저장되고 해시·resetToken 이 응답에 나온다
TOKEN=$(curl -s localhost:3000/login --json '{"username":"alice","password":"alice-lab-pass"}' | jq -r .token)
curl -s -X PATCH localhost:3000/users/me -H "Authorization: Bearer $TOKEN" --json '{"nickname":"앨리스","role":"ADMIN"}' -w "%{http_code}\n"
curl -s -X PATCH localhost:3000/lab/users/me -H "Authorization: Bearer $TOKEN" --json '{"nickname":"앨리스","role":"ADMIN"}'
# 4-1 가격 변조: 가격·자격 필드는 400 (unrecognized_keys), 금액은 서버가 계산
curl -s localhost:3000/orders -H "Authorization: Bearer $TOKEN" --json '{"productId":7,"qty":1,"price":100,"couponOk":true}'

# 4-2 저장형 XSS: 77번 글(시드)을 보기 화면으로
curl -s localhost:3000/posts/77/view | grep content         # &lt;img …&gt; (이스케이프)
curl -s localhost:3000/lab/posts/77/view | grep content     # <img …> (실습 모드)
curl -sI localhost:3000/posts/77/view | grep -iE "^content-security|nosniff"

# 4-4 X-Forwarded-For 를 바꿔 250번 보내기 → 100 200 / 150 429
burst() { for i in $(seq 1 250); do curl -so /dev/null -w "%{http_code}\n" \
    -H "X-Forwarded-For: 198.51.100.$i" "$1"; done | sort | uniq -c; }
burst localhost:3000/posts

# 4-5 다른 사이트에서 온 POST 는 403 (세션 쿠키가 있어도)
curl -s -o /dev/null -w "%{http_code}\n" localhost:3000/account/email -H "Origin: https://attacker.example.net" \
  -H "Sec-Fetch-Site: cross-site" --json '{"email":"x@example.net"}'

# 5-2·5-4 관리자 회원 조회(접속기록이 data/audit/privacy-access.jsonl 에 남는다) · 5-5 내보내기
ADMIN=$(curl -s localhost:3000/login --json '{"username":"admin","password":"admin-lab-pass"}' | jq -r .token)
curl -s localhost:3000/admin/members/1 -H "Authorization: Bearer $ADMIN"                   # phone: 010-****-6789
curl -s "localhost:3000/admin/members?phone=01034567890" -H "Authorization: Bearer $ADMIN"  # 블라인드 인덱스로 bob
curl -s "localhost:3000/admin/members/export?page=0&fields=nickname,email" -H "Authorization: Bearer $ADMIN"
# 5-4 최소 수집: 주민등록번호를 보내면 400
curl -s -o /dev/null -w "%{http_code}\n" localhost:3000/signup --json \
  '{"email":"erin@example.com","password":"erin-lab-pass-1234","nickname":"erin","over14":true,"residentNo":"900101-1234567"}'

# 결제 웹훅: 서명된 curl 명령을 만들어 준다
node scripts/sign-webhook.js evt_000001 | sh
```

## 테스트

```bash
npm test                              # 전체 (DB 불필요)
npm test -- tests/attack.test.js      # 파일 하나만
npm run lint
```

- 테스트는 `NODE_ENV=test` 에서 메모리 대역으로 돕니다. 외부 DB·네트워크가 필요 없습니다.
- 각 테스트 파일의 `수정 전 코드(/lab)는 뚫린다` 묶음은 취약 구현에서 공격이 **성공**하는 것을 확인합니다. 보안 구현 테스트가 "수정 전 코드에서 실패하는지" 비교해 볼 수 있습니다.
- ESM 프로젝트라서 Jest 에 `--experimental-vm-modules` 가 필요합니다. `npx jest ...` 대신 `npm test -- ...` 를 쓰세요(플래그가 들어 있습니다).
- 커맨드 인젝션의 취약 구현 테스트는 실제 셸에서 `echo` 만 실행합니다.

## 폴더 구조

```
node/
├── src/
│   ├── server.js        # 진입점 (LAB_MODE 바인딩 검사)
│   ├── app.js           # 테스트·수업용 기본 앱: import { app } from "../src/app.js"
│   ├── create-app.js    # 미들웨어·라우터 조립
│   ├── config.js
│   ├── routes/          # 보안 구현 (/api)
│   ├── middleware/      # 6-2 bulk-lookup.js (계정별 조회량 감시)
│   ├── clients/         # 5-1 내부 API 클라이언트 (사내 CA)
│   ├── jobs/            # 5-4 파기 배치 (purgeExpired)
│   ├── lab/             # ⚠️ 의도적으로 취약한 실습 코드 (/lab)
├── views/               # 4-2 EJS 화면 (post.ejs · post-rich.ejs)
│   ├── lib/             # auth, password(Argon2id·이전), mask, logger, net(isPublic), idempotency, safe-merge, sanitize-html, origin-check,
│   │                    # envelope·local-kms(5-2), secrets(5-3), privacy-access·audit-log(5-4), export-guard(5-5), kms-jwt(5-7) …
│   └── data/            # Prisma·MongoDB 연결, 메모리 대역, 시드, stores(발급 기록·리프레시 토큰·로그인 실패·패스키)
├── prisma/schema.prisma
├── prisma.config.mjs
├── tests/               # Jest + supertest 공격 재현 테스트 (fixtures/tls: 5-1 테스트용 인증서, 개인 키 없음)
├── scripts/sign-webhook.js · start.js(컨테이너 시작: db push → 서버)
├── data/files/          # /files 로 내려받는 첨부 파일
└── Dockerfile
```

## 수업 화면과 다른 점 (섹션 3)

- 3-1: 해시 출력은 `m=19456,p=1,t=2` 순서입니다. s8(로그인 한 번으로 바뀌는 해시)은 alice 가 **아직 로그인한 적 없는** DB 에서 그대로 재현됩니다. 앞 수업에서 이미 alice 로 로그인했다면 첫 조회부터 `$argon2id$…` 가 나옵니다. 이때는 `docker compose down -v` 후 다시 띄우거나(DB 초기화), DB 없이 `npm run dev:lab` 으로 띄우거나(재시작할 때마다 시드 상태), 아직 로그인하지 않은 계정(예: bob, `bob-lab-pass`)으로 바꿔 확인하세요.
- 3-1·3-2: 화면 터미널(3-1 s8, 3-2 s9)은 `https://api.example.com` 을 씁니다. 로컬에서 따라 할 때는 `http://localhost:3000` 으로 바꾸면 같은 결과가 나옵니다.
- 3-2: 리프레시 토큰 쿠키 `path` 는 `/token`(짧은 경로) 또는 `/api/token`(요청한 경로에 맞춤)입니다. 세션 쿠키 `secure` 는 기본 `auto` 라 HTTP 로 실습해도 동작합니다(운영은 `COOKIE_SECURE=true`).
- 3-3: IdP 설정은 첫 요청 때 discovery 로 읽습니다(화면은 모듈 최상위 `await`). 처음 보는 `iss`+`sub` 는 이메일로 기존 계정에 붙이지 않고 새 계정(`oidc:<sub>`)을 만듭니다.
- 3-4: s4 의 재현은 실습 모드 서버에서 합니다. `/lab/login`·`/lab/orders/:id` 는 수정 전 코드, `/api/login`·`/api/orders/:id` 는 고친 코드(남의 주문 403)입니다. Compose 의 `express` 컨테이너는 실습 모드가 아니므로 `docker compose stop express` 후 `npm run dev:lab`(DB 없이) 또는 `LAB_MODE=vulnerable HOST=127.0.0.1 npm start`(DB 와 함께)로 3000 번 포트에 띄웁니다. 화면은 응답을 `jq -c '{id, userId}'` 로 줄였습니다.
- 3-6: 오리진·RP ID 는 화면의 `config.webauthnOrigin`·`config.webauthnRpId` 처럼 `WEBAUTHN_ORIGIN`·`WEBAUTHN_RP_ID` 값을 씁니다(기본 localhost). 세션 저장소는 메모리(`MemoryStore`, 시작 시 경고 출력)입니다.

## 수업 화면과 다른 점 (섹션 4)

- 4-1: 실습 모드 `PATCH /lab/users/me` 는 `/api/login` 이 발급한 토큰(RS256)을 쓰고 실제 `users` 컬렉션을 고칩니다(다시 로그인하면 ADMIN). 로그인할 때 해시가 Argon2id 로 이전되므로(3-1) 응답의 `passwordHash` 는 `scrypt$…` 가 아니라 `$argon2id$…` 일 수 있습니다. 시드 사용자에 `nickname`·`bio`·`resetToken`(가짜 `rt_EXAMPLE_…`)이 들어 있습니다(새 DB 에만).
- 4-1: 모바일 가격 변조 시나리오 테스트는 `tests/price-tamper.test.js` 에 따로 두었습니다. 주문 응답의 `id` 는 화면처럼 새 DB 에서 1011 부터입니다.
- 4-2: 화면의 77번 글은 시드로 미리 들어 있습니다(`POST /posts` 로 새로 쓰면 6번부터 번호가 붙습니다). 화면의 DOMPurify 예시는 화면처럼 보기 화면과 따로 `GET /posts/:id/rich` (`views/post-rich.ejs`)에 있습니다. `views/post.ejs` 에는 nonce 를 붙인 인라인 스크립트 한 줄이 들어 있습니다. 실습 모드 보기 화면은 "수정 전 서버"처럼 CSP 헤더를 지웁니다.
- 4-2: CSP 헤더는 helmet 기본 지시어에 `script-src 'self' 'nonce-…'` 가 더해진 모양입니다.
- 4-3: 깨진 JSON 테스트("형식이 잘못된 JSON 본문은 서명이 맞아도 400")가 더해져 `webhook.test.js` 는 8개입니다.
- 4-4: `trust proxy` 는 화면의 `1` 을 그대로 쓰지 않고 `TRUST_PROXY` 로 받으며 기본은 `false` 입니다. 앞에 프록시가 없는 로컬·compose 에서 `1` 이면 클라이언트가 보낸 `X-Forwarded-For` 를 믿게 되기 때문입니다. 로드밸런서 한 단계 뒤에 배포할 때 `TRUST_PROXY=1` 로 둡니다(테스트 `rate-limit.test.js` 가 이 상황을 재현). **프록시 신뢰 설정은 스테이징에서 실제 경로로 확인하세요.**
- 4-4: 화면의 `RedisStore` 는 `RATE_LIMIT_REDIS_URL` 이 있을 때만 켭니다(셸에 흔히 잡혀 있는 `REDIS_URL` 로 외부 Redis 에 붙는 일을 막기 위해 이름을 따로 둠). `node-redis` 6 클라이언트를 씁니다.
- 4-4: 화면의 주문 id `1012` 는 4-1 실습(s9)에서 주문 하나(1011)를 만든 뒤 같은 DB 를 쓴다고 가정한 번호입니다. 새 DB 면 1011, 그 사이에 주문을 더 만들었다면 더 큰 번호가 나옵니다.
- 4-5: 출처 확인 미들웨어(`src/lib/origin-check.js`)는 화면과 같은 규칙입니다. `GET`·`HEAD`·`OPTIONS`, `Sec-Fetch-Site: same-origin`·`none`, 허용 목록의 `Origin`, 그리고 `Origin`·`Sec-Fetch-Site` 가 **둘 다 없는** 요청(curl·모바일 앱·서버 간 호출)을 통과시킵니다. 저장소는 허용 목록을 `CORS_ORIGINS` 에서 읽고, 미들웨어를 `originCheck(allowed)` 함수로 감쌌습니다. 데모 `POST /account/email` 은 세션 쿠키(`sid`, 3-3·3-6 로그인)로 인증합니다.

## 수업 화면과 다른 점 (섹션 5)

- 5-1: 화면의 `new https.Agent({ ca: fs.readFileSync(INTERNAL_CA) })` 는 `createInternalApi({ baseUrl, caFile })` 안에 있습니다(`keepAlive`, https 가 아니면 만들지 않음). Node 의 `fetch` 는 `https.Agent` 를 쓰지 않으므로 이 클라이언트는 `https.get` 을 씁니다. 테스트는 실제 TLS 연결 없이 에이전트 옵션과 인증서 체인(사내 CA·중간자)을 확인합니다.
- 5-2: 화면의 `kms` 는 AWS SDK `KMSClient` 이고, `KMS_KEY_ID` 가 없으면 같은 `send(command)` 모양의 가짜 KMS 를 씁니다(마스터 키가 프로세스에 있으므로 실습 전용). 화면의 `save({ ct, iv, tag, wrappedKey })` 는 `members` 테이블의 `phone_ct`·`phone_iv`·`phone_tag`·`phone_key`(base64) + `phone_idx` 입니다. 평문 데이터 키는 쓰고 나서 0 으로 지웁니다.
- 5-3: 화면의 `GetSecretValueCommand` 호출은 `secretsManagerSource()` 안에 있고, 회전이 반영되도록 5분만 캐시합니다. `SECRETS_SOURCE=aws` 일 때 `src/server.js` 가 시작하면서 DB 주소·웹훅 시크릿을 읽습니다(테스트·compose 는 환경 변수).
- 5-4: 마스킹 모양은 화면과 같습니다(Spring 도 같은 모양으로 맞춤). 화면의 주문 로그(`phone`·`card`)는 예제 주문이 연락처·카드번호를 받지 않아 `userId`·`orderId` 만 남깁니다. 접속기록 미들웨어 `createPrivacyAccess(auditLog)` 와 관리자 회원 조회 `GET /admin/members/:id` 는 화면과 같습니다(로그인 → 접속기록 → 관리자 확인 순서라 403 시도도 남음). 접속기록은 JSONL 파일(append only, 테스트는 메모리)에 씁니다. 파기 배치(화면은 Spring)도 `src/jobs/purge-expired.js` 로 같이 넣었고, 화면처럼 탈퇴 회원(30일)과 주문(5년) 보존 기간을 나눴습니다.
- 5-5: 화면의 `prisma.member.findMany`, 탈퇴 회원 제외(`where: { withdrawnAt: null }`), `fields=` 컬럼 허용 목록(기본 `id,nickname`)은 저장소와 같습니다. 화면의 `page(req)`·`actorId` 는 줄여 쓴 것이고, 저장소는 페이지 번호를 zod 로 검증하고 `actorId: req.user.sub` 로 넘깁니다. 알림은 `deps.alert("bulk-export", { actorId, total })`(기본: 경고 로그), 기준은 `EXPORT_ALERT_THRESHOLD`, 누적 카운터는 서버 메모리입니다.
- 5-7: 화면의 헤더 `kid`, `SignCommand` 의 `MessageType: "RAW"`, `issued.save(jti, exp, sub)`(3-6 "모든 기기 로그아웃" 색인용 `sub`)는 저장소와 같습니다. `JWT_KMS_KEY_ID` 가 없으면 기존처럼 프로세스 RS256 키로 서명합니다. 발급 기록은 실습용 메모리 저장소이고, `ops/sql/find_unissued_tokens.sql` 의 `access_log`·`token_issued` 는 감사 DB 예시(`ops/sql/audit_schema.sql`)입니다.

## 수업 화면과 다른 점 (섹션 6)

- 6-2: 화면과 저장소 모두 계정은 JWT 클레임 `req.user.sub`, 이벤트는 `{ user, ip, count }`(300·1001) 입니다(화면 s9 의 jq `.count` 출력과 맞춤). 저장소는 300·1000 을 상수 `ALERT_AT`·`BLOCK_OVER` 로 둡니다. `redis` 자리에는 `RATE_LIMIT_REDIS_URL` 이 있으면 node-redis 클라이언트(`incr`·`expire`), 없으면 같은 모양의 메모리 카운터(`src/lib/lookup-counter.js`)가 들어갑니다. 화면의 `app.use("/api/addresses", requireAuth, …)` 는 다른 라우트처럼 `GET /addresses`(`/api/addresses` 도 같음) 라우터 안에 있고, 배송지는 실습용 가짜 값입니다. 보안 이벤트는 `security.log`(기본 `data/log/security.log`, 화면은 `/var/log/board/security.log`)에 쓰고 같은 내용을 경고 로그로도 남깁니다. fail2ban 필터가 세는 `authz_denied` 는 `requireRole` 의 403 에서 남깁니다(`validation_reject` 는 아직 남기는 곳이 없습니다).
- 6-5: Dockerfile 은 화면처럼 `npm ci --omit=dev --ignore-scripts` 를 쓰고, 끈 설치 스크립트 중 필요한 것(`npm rebuild @prisma/engines prisma`, `prisma generate`)만 다시 실행합니다. Prisma 가 실행 이미지(OpenSSL 3)에 맞는 엔진을 받도록 빌드 단계에 `openssl` 을 설치합니다. 실행 이미지에는 셸이 없으므로 `CMD ["src/server.js"]` 대신 `scripts/start.js`(스키마 반영 후 서버 시작)를 실행하고, `views/`·`data/files` 도 복사합니다. 코드는 root 소유, `data/` 만 65532 소유입니다. **Dockerfile 은 명령 줄 끝 주석을 지원하지 않으므로**(`FROM … # …` 은 빌드 오류, `USER 65532:65532 # …` 는 주석까지 값이 됨) 화면과 저장소 모두 `FROM`·`USER` 의 주석을 윗줄에 둡니다(`RUN` 줄 끝의 `#` 은 셸 주석이라 동작합니다). 실행 단계 베이스 이미지도 화면처럼 다이제스트로 고정했습니다. 실행 이미지에 `ping` 이 없어서 컨테이너에서는 `GET /admin/ping` 이 502 입니다(2-4 실습은 로컬 실행으로).
