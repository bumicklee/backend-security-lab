# backend-security-lab

「백엔드 개발자를 위한 서버 보안 실무」 실습 저장소입니다. 같은 예제 서비스(게시판, 파일 업로드, 결제 웹훅, 로그인)를 **Spring Boot 4**와 **Express 5** 두 가지로 만들었습니다. 수업에 나온 공격을 직접 재현하고, 수정한 코드가 막아 내는 것을 테스트로 확인합니다.

> **반드시 지켜 주세요.** 이 저장소에는 수업용으로 **일부러 취약하게 만든 코드**가 들어 있습니다. 내 PC나 내가 관리하는 실습 VM에서만 실행하고, 인터넷에 열지 마세요. 허가받지 않은 시스템에 수업 내용을 시험하는 것은 불법입니다.

> 체크리스트·명령어 정리 PDF는 이 저장소에 없습니다. 인프런 강의의 **수업자료**에서 받으세요.

## 구성

```text
backend-security-lab/
├── compose.yaml        # db(PostgreSQL 17) · mongo(MongoDB 8) · redis(요청 제한 카운터) · spring(:8080) · express(:3000)
├── db/init/            # 데이터베이스 2개 생성(board_spring, express_board)
├── spring/             # Spring Boot 4 · Java 25 · Gradle   → spring/README.md
├── node/               # Express 5 · Node 24 LTS(22 이상) · Prisma · MongoDB → node/README.md
├── ops/backup/         # 5-6 restic 백업·검사·복구 훈련 스크립트 → ops/backup/README.md
├── ops/sql/            # 5-7 발급 기록에 없는 토큰 찾기 · 감사 DB 예시 스키마 · 6-10 audit.read_events·범위 산정 쿼리
├── ops/fail2ban/       # 6-2 보안 이벤트(security.log) 필터·jail
├── ops/hunting/        # 6-8 지속성 헌팅 명령 모음 (읽기만)
├── ops/ir/             # 6-9 휘발성 정보 수집 스크립트 (ir-collect.sh)
├── ops/kev/            # 7-3 KEV 자동 대조 스크립트 (kev_watch.py · kev_daily.sh · notify.sh · cron · 모의 파일) → ops/kev/README.md
├── detections/linux/   # 6-8 Sigma 규칙(systemd 서비스 생성) + 그 입력인 auditd 감시 규칙(60-persistence.rules)
├── k8s/                # 6-6 PSA restricted 네임스페이스 · 파드 보안 설정 · NetworkPolicy · RBAC · 연결 확인 파드
├── docs/ci/            # 5-3 OIDC 단기 자격 증명 배포 워크플로 예시 (활성화하지 않음)
├── docs/incident/      # 6-10 유출 통지문 틀 · 비난 없는 회고 틀
├── .gitleaks.toml      # 5-3 시크릿 스캔 설정 (기본 규칙 + 실습용 가짜 값 허용)
├── .pre-commit-config.yaml       # 5-3 커밋 전 gitleaks 검사
├── .trivyignore.yaml   # 6-1 스캔 예외 (근거·만료일)
├── .semgrep/board.yml  # 6-3 팀 Semgrep 규칙 (+ 규칙 테스트용 board.js·board.java)
├── zap-rules.conf      # 6-3 ZAP 규칙 파일
├── .github/dependabot.yml        # 6-4 업데이트 대기 기간(cooldown)
└── .github/workflows/  # attack-regression(2-8) · secret-scan(5-3) · rescan-prod(6-1) · pr-security·dast-staging(6-3) · release(6-4)
```

## 빠르게 시작하기

준비물: Docker(Compose 포함). 테스트만 돌리려면 Docker 없이 JDK 17 이상(Gradle이 JDK 25를 자동으로 받음) 또는 Node 22 이상(권장 24 LTS)만 있어도 됩니다.

```bash
git clone https://github.com/bumicklee/backend-security-lab.git
cd backend-security-lab
docker compose up -d --build --wait     # 이미지를 빌드하고, 모두 healthy 가 될 때까지 기다림
docker compose ps                       # lab-db-1 · lab-mongo-1 · lab-redis-1 · lab-spring-1 · lab-express-1
curl -s 127.0.0.1:8080/health; echo     # {"status":"UP"}
curl -s 127.0.0.1:3000/health; echo     # {"status":"ok"}
```

모든 포트는 `127.0.0.1`에만 열립니다. 원격 VM에서 실행했다면 내 PC에서 SSH 터널로 접속합니다.

```bash
ssh -L 8080:127.0.0.1:8080 -L 3000:127.0.0.1:3000 admin@lab-vm.example.com
```

정리: `docker compose down` (데이터까지 지우려면 `docker compose down -v`)

**`localhost:3000`에서 다른 화면이 나오면** 내 PC에서 다른 프로그램(예: 프런트엔드 개발 서버)이 3000번 포트를 쓰고 있는 것입니다. `curl -s 127.0.0.1:3000/health`처럼 IPv4 주소로 접속하거나, 그 프로그램을 끄세요.

### GitHub Actions 워크플로를 켜는 법

fork한 저장소에서 `attack-regression`(PR), `secret-scan`(push·PR), `pr-security`(PR)는 바로 돌아갑니다. 배포·운영 예시인 아래 세 워크플로는 레지스트리·AWS 역할·스테이징 주소가 예시 값이라 **기본으로 꺼 두었습니다.** 내 환경에 맞게 값을 바꾼 뒤 **Settings → Secrets and variables → Actions → Variables**에서 변수를 `true`로 만들면 켜집니다.

| 워크플로 | 수업 | 켜는 변수 |
| --- | --- | --- |
| `release.yml` | 6-4 | `ENABLE_RELEASE` |
| `rescan-prod.yml` | 6-1 | `ENABLE_RESCAN` |
| `dast-staging.yml` | 6-3 | `ENABLE_DAST` |

수업 화면에는 이 `if:` 줄이 없습니다.

## 안전한 코드와 취약한 코드

| 경로 | 내용 | 켜지는 조건 |
| --- | --- | --- |
| `/api/...` | 수업에서 고친 **안전한 코드** | 항상 |
| `/lab/...` | 수업에서 공격을 재현하는 **취약한 코드** | 실습 모드 + `127.0.0.1`에서만 |

Express는 안전한 코드를 루트 경로(`/posts`, `/files` …)에도 열어 두어, 수업 화면의 짧은 주소를 그대로 쓸 수 있습니다.

**취약한 실습 모드는 `docker compose up`으로는 켜지지 않습니다.** 컨테이너는 외부 포트를 열기 때문에, 두 서비스 모두 실습 모드에서는 `127.0.0.1`이 아니면 시작을 거부합니다. 실습 VM에서 직접 실행합니다(DB는 compose로 띄운 것을 그대로 씁니다).

```bash
docker compose up -d db mongo             # DB만 띄우기

# Spring
cd spring && ./gradlew bootRun --args='--spring.profiles.active=lab-vulnerable'

# Express
cd node && npm ci && LAB_MODE=vulnerable HOST=127.0.0.1 npm start
#   DB 없이: npm run dev:lab
```

## 테스트

공격 재현 테스트는 DB나 Docker 없이 돌아갑니다.

```bash
cd spring && ./gradlew test        # JUnit 5 + MockMvc (H2)
cd node && npm test                # Jest + supertest
npm test -- tests/injection.test.js   # 파일 하나만 (ESM이라 npx jest 대신 npm test -- 사용)
```

코드를 바꾸지 않고 `./gradlew test` 를 다시 돌리면 Gradle 이 테스트를 건너뜁니다(up-to-date). 결과 줄을 다시 보려면 `./gradlew test --rerun` 을 쓰세요.

각 테스트는 "수정한 코드(`/api`)는 공격을 막는다"와 "수정 전 코드(`/lab`)는 뚫린다"를 함께 확인합니다.

## 수업별 위치

| 수업 | Spring | Express |
| --- | --- | --- |
| 0-2 토큰 서명키와 발급 기록 | — | `POST /login`, `GET /me` · `auth.jwt.test.js` |
| 0-3 로그 마스킹 | `support.Mask` · `MaskTest` | `lib/mask.js` · `mask.test.js` |
| 0-5 실습 환경 | `GET /health`, `SecurityConfig` | helmet, rate limit, `express.json({limit})` |
| 2-2 입력 검증 | `POST /api/orders` · `OrderValidationTest` | `POST /orders` (zod) · `orders.validation.test.js` |
| 2-3 SQL·NoSQL 인젝션 | `GET /api/posts?title=` · `PostSearchAttackTest` | `GET /posts?title=`, `POST /login` · `injection.test.js` |
| 2-4 커맨드 인젝션·경로 조작 | `/api/admin/ping`, `/api/files` · `CommandPathAttackTest` | `/admin/ping`, `/files` · `command-path.test.js` |
| 2-5 파일 업로드 | `POST /api/avatars` · `AvatarUploadAttackTest` | `POST /avatars` · `avatars.test.js` |
| 2-6 SSRF | `GET /api/preview?url=` · `PreviewSsrfTest` | `GET /preview?url=` · `preview.ssrf.test.js` |
| 2-7 역직렬화·프로토타입 오염 | `POST /api/carts` · `DeserializationAttackTest` | `PATCH /me/settings` · `prototype-pollution.test.js` |
| 2-8 공격 재현 테스트 | `AttackRegressionTest` · CI: `.github/workflows/attack-regression.yml` | `attack.test.js` (CI는 `npm test` 전체) |
| 3-1 비밀번호 저장 | `DelegatingPasswordEncoder`(argon2·bcrypt), `UserDetailsPasswordService` · `PasswordStorageTest` | `lib/password.js` `verifyAndUpgrade` · `password.test.js` |
| 3-2 세션 vs JWT | 세션 쿠키 속성(`application.yml`), `POST /api/auth/logout` · `AuthLoginTest` | `POST /token/refresh`(회전·재사용 탐지), JWT `issuer` 검증 · `refresh.test.js` |
| 3-3 OAuth 2.0·OIDC | `oidc` 프로필, `/oauth2/authorization/idp` · `OAuthLoginTest` | `GET /login/idp`, `GET /callback`(openid-client 6) · `oidc.test.js` |
| 3-4 IDOR·BOLA | `GET /api/orders/{id}`, `@PostAuthorize`·`@PreAuthorize` · `OrderAuthorizationTest` | `GET /orders/:id` · `orders.authz.test.js`, `orders.idor.test.js` |
| 3-5 무차별 대입 | `LoginAttempts`(429 + Retry-After), `CompromisedPasswordChecker` · `LoginProtectionTest` | `POST /login` 계정별 실패 누적 · `login.bruteforce.test.js` |
| 3-6 MFA·세션 탈취 | `@EnableMultiFactorAuthentication`, `webAuthn()`, `POST /api/me/password` · `MfaSessionTest` | `routes/passkey.js`, `POST /me/sessions/revoke-all` · `passkey.test.js` |
| 3-7 인증·인가 테스트 | 인가 매트릭스 · `OrderAuthorizationTest` | 인가 매트릭스 · `orders.authz.test.js` |
| 4-1 Mass assignment·과도한 노출 | `PATCH /api/users/me`(`ProfileUpdate`·`ProfileView`) · `ProfileUpdateTest` | `PATCH /users/me`(`z.strictObject` + `view`) · `profile.test.js`, `price-tamper.test.js` |
| 4-2 XSS·CSP | `GET /api/posts/{id}/view`(Thymeleaf `th:text`), `/rich`(OWASP Sanitizer), CSP 헤더 · `PostXssTest`, `RichTextSanitizerTest` | `GET /posts/:id/view`(EJS `<%= %>`), `/rich`(DOMPurify), helmet CSP nonce · `xss.test.js` |
| 4-3 웹훅 서명 | `POST /api/webhooks/payments` · `WebhookSignatureTest` | `POST /webhooks/payment` · `webhook.test.js` |
| 4-4 Rate limiting·멱등성 | `RateLimitFilter`(Bucket4j), `server.tomcat.remoteip.*` · `RateLimitFilterTest` | `trust proxy`, express-rate-limit(+Redis), Idempotency-Key · `rate-limit.test.js`, `orders.validation.test.js` |
| 4-5 CORS·CSRF | `csrf(c -> c.spa())`, `CorsConfigurationSource` · `CsrfCorsTest` | `cors`(허용 목록), 출처 확인 미들웨어, `POST /account/email` · `csrf.test.js` |
| 섹션 4 `/lab` 대조 | `LabVulnerableDemoTest` | `lab-section4.test.js` |
| 5-1 TLS·인증서 검증 | `tls.InternalApiConfig`(SSL 번들 `internal` → `RestClient internalApi`, `internal-api` 프로필) · `InternalApiTlsTest` | `src/clients/internal-api.js`(`https.Agent({ ca })`) · `tls.internal-api.test.js` |
| 5-2 저장 데이터 암호화·KMS | `crypto.FieldCrypto`(KMS 데이터 키 + `AES/GCM/NoPadding` + 블라인드 인덱스), `crypto.LocalKmsClient` · `EnvelopeEncryptionTest` | `src/lib/envelope.js`(`GenerateDataKeyCommand` + `aes-256-gcm` + HMAC 인덱스), `src/lib/local-kms.js` · `envelope.test.js` |
| 5-3 시크릿 관리 | `aws` 프로필(`spring.config.import: optional:aws-secretsmanager:…`) | `src/lib/secrets.js`(`SECRETS_SOURCE=aws`) · `secrets.test.js` · 공통: `.gitleaks.toml`, `.pre-commit-config.yaml`, `.github/workflows/secret-scan.yml`, `docs/ci/deploy-oidc.yml` |
| 5-4 개인정보보호법 관점의 개발 | `support.Mask`, `POST /api/auth/signup`(`SignupRequest`), `privacy.PrivacyAccessInterceptor`(`@PrivacyAccess`), `privacy.RetentionJob#purgeExpired` · `MaskTest`, `PrivacyDevTest` | `lib/mask.js`, `POST /signup`, `src/lib/privacy-access.js`, `src/jobs/purge-expired.js` · `mask.test.js`, `privacy.test.js` |
| 5-5 이그레스 통제와 DLP | `GET /api/admin/members/export`(1,000건씩·컬럼 허용 목록·누적 알림) · `MemberExportTest` | `GET /admin/members/export`(`orderBy: { id: "asc" }`, `take: 1000`) · `export.test.js` |
| 5-6 랜섬웨어도 못 지우는 백업 | `ops/backup/`(`backup.sh` · `check.sh` · `restore-drill.sh` · `prune.sh` · `iam-backup-writer.json`) | (같음) |
| 5-7 유출 사고 재구성 | — (Spring 은 세션 방식) | `src/lib/kms-jwt.js`(KMS `SignCommand` + `issued.save`), `JWT_KMS_KEY_ID` · `kms-jwt.test.js` · 공통: `ops/sql/find_unissued_tokens.sql` |
| 6-1 취약점 점검 운영 | `.trivyignore.yaml`, `.github/workflows/rescan-prod.yml` | (같음) |
| 6-2 탐지하고 자동으로 막기 | `GET /api/addresses` · `detect.BulkLookupGuard`, `detect.SecurityEvents`, `detect.AuthorizationDeniedLogger` · `BulkLookupGuardTest` | `GET /addresses` · `src/middleware/bulk-lookup.js`, `src/lib/security-events.js` · `bulk-lookup.test.js` · 공통: `ops/fail2ban/` |
| 6-3 SAST·SCA·DAST | `.github/workflows/pr-security.yml`(Semgrep·CodeQL·의존성 검토), `.semgrep/board.yml`, `.github/workflows/dast-staging.yml`, `zap-rules.conf` | (같음) |
| 6-4 공급망 | `.github/workflows/release.yml`(SHA 고정·최소 권한·OIDC·SBOM·cosign·SLSA), `.github/dependabot.yml` | (같음) |
| 6-5 컨테이너 | `compose.yaml` `spring`(read_only·tmpfs·cap_drop·no-new-privileges, UID 10001) | `node/Dockerfile`(distroless nonroot 65532) + `compose.yaml` `express` |
| 6-6 쿠버네티스 | `k8s/`(`namespace.yaml`·`board-api.yaml`·`network-policy.yaml`·`rbac.yaml`·`nettest.yaml`·`debug-pod.yaml`) | (같음) |
| 6-7 클라우드 IAM | — (저장소 예제 없음, 화면 참고) | — |
| 6-8 위협 헌팅 | `ops/hunting/hunt-persistence.sh`, `detections/linux/new-systemd-service.yml`, `detections/linux/60-persistence.rules` | (같음) |
| 6-9 사고 첫 한 시간 | `ops/ir/ir-collect.sh` | (같음) |
| 6-10 사고 이후 | `ops/sql/audit_read_events.sql`·`breach_scope.sql`, `docs/incident/breach-notice-template.md`·`postmortem-template.md` | (같음) |
| 7-3 KEV 자동 대조 스크립트 | `ops/kev/`(`kev_watch.py` · `kev_daily.sh` · `notify.sh` · `kev-watch.cron` · `mock/`) | (같음) |

자세한 경로·클래스·환경 변수는 `spring/README.md`, `node/README.md`에 있습니다.

## 수업 화면과 다른 점

수업 화면은 핵심만 보여 주기 위해 코드를 줄였습니다. 저장소는 실제로 동작하도록 다음이 더해져 있습니다.

- 경로: 화면의 `/posts`, `/files` 등은 Spring에서 `/api/posts`, `/api/files`입니다(Express는 둘 다 지원).
- 인증: `/avatars`, `/orders`, `/me/settings`, `/admin/ping`은 로그인이 필요합니다(`/admin`은 관리자만). 테스트는 먼저 로그인합니다.
- 로그인: Express 안전한 로그인은 사용자 이름으로 찾은 뒤 비밀번호 해시를 검증합니다.
- 취약한 SSRF 실습은 실제 클라우드 메타데이터 대신 로컬 가짜 엔드포인트를 대상으로 합니다.
- 실습용 계정과 비밀번호(`admin` / `alice` / `bob` 등)는 각 README에 있으며 모두 예시 값입니다.
- 섹션 3: 주문 1000~1010 시드와 길어진 비밀번호 해시 컬럼(255자)은 새 DB 에만 반영됩니다. 이전에 띄운 적이 있다면 `docker compose down -v` 후 다시 띄우세요.
- 섹션 3에서 화면과 달라진 부분은 각 README의 "강의 화면과 다른 점"·"수업 화면과 다른 점"에 정리했습니다.
- 섹션 4: 사용자 문서의 `nickname`·`bio`·`resetToken`(가짜 값)과 게시글 77번(XSS 예시) 시드도 새 DB 에만 들어갑니다. 마찬가지로 `docker compose down -v` 후 다시 띄우세요.
- 섹션 5: 회원 테이블(`members`, 전화번호는 암호문 + 블라인드 인덱스), 주문 `created_at` 컬럼, 접속기록 테이블(`privacy_access_log`, Spring)이 새로 생깁니다. 회원 시드(alice·bob·탈퇴한 carol)는 새 DB 에만 들어갑니다. `docker compose down -v` 후 다시 띄우세요.
- 섹션 5 에서 화면과 달라진 부분은 아래 "섹션 5" 목록과 각 README 에 정리했습니다.
- 섹션 6: compose 의 `spring`·`express` 는 읽기 전용 루트로 돌고, 쓰기는 `/tmp`(메모리)와 새 볼륨 `spring-data`·`express-data`(`/app/data`)에만 합니다. 이전에 띄운 적이 있다면 `docker compose down -v` 후 `docker compose up -d --build` 로 다시 띄우세요. 섹션 6 에서 화면과 달라진 부분은 아래 "섹션 6" 목록에 정리했습니다.
- 섹션 4-4: 프록시 신뢰 설정(Express `TRUST_PROXY`, Spring `server.tomcat.remoteip.internal-proxies`)은 실제로 거치는 로드밸런서 구성에 따라 달라집니다. 운영에 올리기 전 **스테이징에서 `X-Forwarded-For` 를 바꿔 보내도 같은 키로 세는지 꼭 확인**하세요. 로컬·compose 처럼 앞에 프록시가 없으면 Express 는 `TRUST_PROXY=false`(기본값)여야 합니다.

### 섹션 2

- 2-1 s7(영향도 판단)의 터미널(`ops@legacy-01:/srv/board`)은 가상의 오래된 사내 서비스입니다. 이 저장소에는 log4j-core와 report-kit이 없으므로 같은 명령을 돌려도 결과가 다릅니다.
- 2-8 s6은 수정을 아직 커밋하지 않은 상황입니다(고친 파일을 `git stash`로 잠시 치워 두고 테스트가 실패하는지 확인). 이 저장소에는 수정본이 이미 커밋되어 있어 `git stash push`가 "No local changes to save"를 냅니다. 실패를 직접 보려면 `spring/src/main/java/board/PostJdbcSearch.java`의 `search()` 쿼리를 잠시 문자열 결합으로 바꾼 뒤(`" WHERE title LIKE '%" + keyword + "%' ORDER BY " + column + " " + direction`, 마지막 인자 삭제) `./gradlew test --tests '*AttackRegressionTest'`를 돌리고, `git checkout -- spring/src/main/java/board/PostJdbcSearch.java`로 되돌리세요. 4개 중 2개가 실패합니다.
- 테스트 출력 화면은 핵심 줄만 보이고 나머지는 "… 외 N개"로 줄였습니다. 실행 시간(ms, s)은 PC마다 다릅니다.

### 섹션 5

- 5-1: 화면의 `new https.Agent({ ca: fs.readFileSync(INTERNAL_CA) })` 는 Express `src/clients/internal-api.js` 의 `createInternalApi({ baseUrl, caFile })` 안에 있고, `INTERNAL_API_URL`·`INTERNAL_CA_FILE` 이 둘 다 있을 때만 만듭니다.
  Spring 의 SSL 번들(`spring.ssl.bundle.pem.internal`)은 화면처럼 `internal-api` 프로필(`application-internal-api.yml`)에 있고, `INTERNAL_CA_FILE`(기본 `file:/etc/board/internal-ca.pem`)을 씁니다. `RestClientSsl` 은 Boot 4 의 `spring-boot-starter-restclient` 에 있습니다.
  테스트는 네트워크 없이 사내 CA·사내 CA 가 서명한 인증서·같은 이름의 중간자 인증서(모두 테스트용, 개인 키는 넣지 않음)로 확인합니다. nginx TLS 설정 파일은 넣지 않았습니다(1-7 에서 다룸).
- 5-2: `KMS_KEY_ID` 가 없으면 실제 KMS 대신 **프로세스 안의 가짜 KMS**(Express `lib/local-kms.js`, Spring `crypto.LocalKmsClient`)가 같은 요청(`GenerateDataKey`·`Decrypt`)을 처리합니다. 실습 전용입니다.
  Express 는 화면의 `save({ ct, iv, tag, wrappedKey })` 를 `members` 테이블의 `phone_ct`·`phone_iv`·`phone_tag`·`phone_key`(base64)와 `phone_idx` 로 저장합니다. Java 는 GCM 태그가 암호문 끝에 붙어 `ct`·`iv`·감싼 키 세 칸입니다. 두 쪽 모두 평문 데이터 키를 쓰고 나서 0 으로 지웁니다.
- 5-3: 화면의 `application-aws.yml`(`spring.config.import: optional:aws-secretsmanager:…`, Spring Cloud AWS 4.2)은 저장소와 같습니다. `aws` 프로필일 때만 읽으므로 기본 실행·테스트는 AWS 에 접속하지 않습니다. Express 는 `SECRETS_SOURCE=aws` 일 때 `prod/board-api/db`·`prod/board-api/webhook` 을 읽고 5분만 캐시합니다.
  화면 s10 의 배포 워크플로는 `docs/ci/deploy-oidc.yml` 에 예시로만 두었습니다(액션은 커밋 SHA 로 고정). 화면에 없는 `.github/workflows/secret-scan.yml`(이력 전체 gitleaks 검사)이 더해졌습니다.
- 5-4: 마스킹 모양을 화면(`010-****-5678`, `****-****-****-1111`)에 맞춰 두 스택을 통일했습니다. Spring `Mask.last4` 는 `****1234` → `****-****-****-1234`, `Mask.email` 은 `a****@` → `a***@` 로 바뀌었습니다(2-7 결제 수단 응답의 `masked` 도 같은 모양).
  주문 로그는 화면처럼 고른 필드만 남기지만, 예제 주문은 연락처·카드번호를 받지 않아 `userId`·`orderId` 만 남깁니다.
  접속기록 미들웨어 `createPrivacyAccess(auditLog)` 와 관리자 회원 조회 경로 `GET /admin/members/:id`(`/api` 도 같음)는 화면과 같습니다. Express 는 권한이 없어 403 이 난 시도도 남기고, Spring 은 URL 규칙(`/api/admin/**`)이 먼저 거절하므로 남지 않습니다.
  파기 배치는 화면처럼 탈퇴 회원(기본 30일, `app.privacy.member-retention`)과 주문(기본 5년, `app.privacy.order-retention`)의 보존 기간을 나눴습니다. 가입 DTO 는 사용자 이름 칸(50자)에 맞춰 이메일에 `@Size(max = 50)` 를 더했습니다.
- 5-5: 화면의 `prisma.member.findMany(...)`, 탈퇴 회원 제외(`where: { withdrawnAt: null }`), 컬럼 허용 목록(`fields=id,nickname,email,createdAt`)은 저장소와 같습니다. 화면의 `page(req)`·`actorId` 는 줄여 쓴 것이고, 저장소는 `actorId: req.user.sub` 로 넘깁니다. 알림은 `deps.alert("bulk-export", …)`(기본: 경고 로그), 기준은 `EXPORT_ALERT_THRESHOLD`(기본 10,000)입니다. 누적 카운터는 서버 메모리입니다.
  화면 s5 의 ufw 규칙은 실습 서버 예시(내부 DNS·NTP 10.0.0.2, 프록시 10.0.2.10, DB 10.0.1.20)이고 저장소에는 없습니다.
- 5-6: 화면의 명령을 `ops/backup/*.sh` 로 묶었습니다. `restic forget --prune` 은 앱 서버가 아니라 관리 서버용 `prune.sh` 에 따로 두었고, 복구 훈련은 이름에 `restore_test` 가 들어간 DB 에만 복원합니다.
- 5-7: KMS 서명은 Express 에만 있습니다(`JWT_KMS_KEY_ID`, 데이터 키용 `KMS_KEY_ID` 도 필요). 화면의 헤더 `kid`, `MessageType: "RAW"`, `issued.save(jti, exp, sub)`(3-6 "모든 기기 로그아웃"용 `sub`)는 저장소와 같습니다. `ops/sql/find_unissued_tokens.sql` 이 읽는 `access_log`·`token_issued` 는 감사 DB 예시(`ops/sql/audit_schema.sql`)이고, 예제 서비스가 직접 쓰지는 않습니다.

### 섹션 6

화면의 `<커밋 SHA>`·`<다이제스트>` 자리는 화면 폭(한 줄 95자) 때문에 남겨 둔 것이고, 저장소에서는 실제 값으로 고정했습니다(2026-10-26 기준). 6-4 s4 의 `actions/checkout` 만은 화면에도 저장소와 같은 실제 SHA(v5.1.0)가 나옵니다. 액션은 Dependabot(`github-actions`)이, 베이스 이미지 다이제스트는 Dependabot(`docker`)이 새 버전을 제안합니다.

- 6-1: `.trivyignore.yaml` 은 화면 그대로입니다(맨 위 설명 주석만 더해짐, CVE 번호는 가짜). `rescan-prod.yml` 은 러너(`ubuntu-24.04`)·`fail-fast: false`·cron·권한·스캔 옵션이 화면과 같고, 화면에서 주석으로 생략한 단계(trivy 0.76.1 설치 + SHA-256 체크섬 확인, 레지스트리 로그인 `docker/login-action`, DB 갱신 `--download-db-only`)와 `workflow_dispatch`, 업로드 조건(`if: always() && hashFiles('out.sarif') != ''`), 체크아웃 `persist-credentials: false` 가 더해졌습니다. 화면의 `registry.example.com/${{ matrix.image }}:prod` 는 `env.REGISTRY` 로 뺐습니다. 레지스트리·이미지 목록·`REGISTRY_USER`·`REGISTRY_READ_TOKEN` 은 예시입니다.
- 6-2: 화면과 저장소 모두 계정 키 `lookup:<계정>:<UTC 날짜>`, 300번째 `bulk_lookup`, 1001번째 `bulk_block`, 1000 초과 429 이고, 이벤트에 조회 수(`count`)를 함께 남깁니다(화면 s9 의 jq `.count`). Express 는 화면처럼 `req.user.sub` 를 씁니다. 나머지 앱 코드 차이(카운터 구현, 등록 위치, 429 본문)는 `node/README.md`·`spring/README.md` 에 있습니다. fail2ban jail 파일 이름은 화면과 같은 `board-security.local` 이고, `failregex`·`logpath`·`maxretry`·`findtime`·`bantime` 은 화면 값 그대로이며, 화면에 없는 `datepattern`(필터)·`port`·`ignoreip`(jail)이 더해졌습니다. `fail2ban-regex` 로 확인했습니다(`authz_denied`·`validation_reject` 만 매칭, `bulk_*` 는 매칭 안 됨). 보안 로그 경로는 서버에서는 `/var/log/board/security.log`, 앱 기본값은 `data/log/security.log`(`SECURITY_LOG_FILE`)입니다. 화면 s2 의 `access.log` 집계와 s9 의 재현 결과는 예시 출력입니다.
- 6-3: `pr-security.yml` 의 러너(`ubuntu-24.04`)·권한·`fetch-depth: 0`·`--baseline-commit`·`fail-on-severity: high` 는 화면과 같습니다. semgrep 은 화면의 `semgrep --config …` 에 `scan` 하위 명령과 `--metrics=off` 를 붙였고(화면은 한 줄 폭 때문에 기본 명령인 `scan` 을 생략), 컨테이너 작업에서 git 이 작업 폴더를 거부하지 않도록 `safe.directory` 단계가 더해졌습니다. 화면의 `dependency-review-action@<SHA>` 는 `<커밋 SHA>` 와 같은 뜻입니다. 화면에서 "저장소 설정으로 켤 수 있다"고 한 CodeQL(v4, `javascript-typescript`·`java-kotlin`, `build-mode: none`)을 `codeql` 작업으로 넣었습니다(기본 설정을 켜면 이 작업은 지웁니다). `.semgrep/board.yml` 은 화면 그대로이고, 규칙 테스트 예시(`board.js`·`board.java`)와 `.semgrepignore` 가 더해졌습니다. `dast-staging.yml` 은 화면과 같이 `-I`(경고만으로는 실패하지 않음)를 쓰고, 화면의 `ghcr.io/zaproxy/zaproxy:stable` 을 다이제스트로 고정했으며(`@sha256:…`, 화면은 13줄 제한 때문에 태그만), 보고서를 아티팩트로 올리고 `workflow_dispatch` 가 더해졌습니다. `zap-rules.conf` 맨 위에 주석 세 줄이 있습니다(화면의 `grep -v '^#'` 출력은 같음). 화면 s2 의 `node/src/routes/search.js` 는 저장소에 없는 PR 예시입니다. 화면 s9 는 `zap-scan.log` 에서 규칙 줄과 요약 줄만 고른 출력입니다(`grep -E '^(IGNORE|WARN|FAIL)' zap-scan.log | tr '\t' ' '`, ZAP 의 요약 줄은 탭 구분).
- 6-4: `release.yml` 의 앞부분(트리거, 기본 읽기 권한, 작업 권한 `contents: read, id-token: write, packages: write, attestations: write`, 러너 `ubuntu-24.04`, SHA 고정, OIDC, `npm ci --ignore-scripts`)은 화면과 같습니다. 화면의 `configure-aws-credentials@<전체 커밋 SHA>` 는 폭 때문에 자리 표시이고 저장소는 v5.1.1 의 실제 SHA 입니다. 화면의 `role-to-assume: arn:aws:iam::111122223333:role/board-release` 는 저장소 변수(`RELEASE_ROLE_ARN`)로 받습니다(버킷은 `SBOM_BUCKET`). 그 뒤에 `setup-node`, 테스트(`prisma generate`), GHCR 이미지 빌드·푸시, SBOM(`anchore/sbom-action`, CycloneDX) → S3 보관, `cosign sign`·`cosign attest`(키리스), `actions/attest-build-provenance`(SLSA) 단계가 더해졌습니다. `dependabot.yml` 은 화면(npm·gradle 의 cooldown 7일, github-actions)에 `github-actions` cooldown 과 `docker` 생태계(`/node`·`/spring`)가 더해졌습니다(화면 13줄 제한). 화면 s6·s7 의 `registry.example.com/board-api@sha256:4f1e9c…`, SBOM 파일 이름, 터미널 출력은 예시이고, 저장소는 GHCR 에 올립니다. `actions/checkout` 은 v5.1.0 기준으로 고정했습니다(2026-10 최신은 v6 계열). SHA 로 고정했으므로 동작은 그대로이고, Dependabot(`github-actions`)이 새 버전을 제안합니다.
  배포 전 확인 명령(화면 s7): `cosign verify ghcr.io/<owner>/<repo>/board-api@sha256:… --certificate-identity-regexp '^https://github.com/<owner>/<repo>/' --certificate-oidc-issuer https://token.actions.githubusercontent.com`, `gh attestation verify --owner <owner> oci://ghcr.io/<owner>/<repo>/board-api@sha256:…`
- 6-5: 화면의 `node/Dockerfile` 도 저장소처럼 `FROM`·`USER` 의 주석을 윗줄에 둡니다(**Dockerfile 은 명령 줄 끝 주석을 지원하지 않습니다.** `FROM … # …` 은 빌드 오류, `USER 65532:65532 # …` 는 주석까지 값이 됨). 두 단계 모두 다이제스트로 고정했습니다. 화면은 핵심만 보여 주므로 저장소에 더해진 것(빌드 단계의 `openssl`, `prisma` 파일 복사와 `npm rebuild`·`prisma generate`, `views/`·`data/` 복사와 `data/` 만 65532 소유, `ENV`·`EXPOSE 3000`·`HEALTHCHECK`, `CMD ["scripts/start.js"]`)은 `node/README.md` 에 있습니다. compose 는 화면의 `board-api` 서비스(`image: …@sha256:<다이제스트>`)가 저장소의 `express` 서비스(`build: ./node`)에 해당하고, 사용자·`read_only`·`tmpfs`·`cap_drop`·`no-new-privileges`·`pids_limit`·`mem_limit`·포트(`127.0.0.1:3000:3000`)는 같습니다. 저장소에는 `/app/data` 볼륨과 환경 변수, `spring` 서비스(UID 10001, 768m)가 더해졌습니다. 화면 s2·s5·s7·s8 의 터미널 출력(크기 수치, PID, `ci-runner` 등)은 예시입니다.
- 6-6: `k8s/network-policy.yaml` 의 ①·② 는 화면과 같고(`apiVersion` 포함), 화면 s8 의 ③ `data-stores`(DB 파드의 들어오기를 board-api 에서만 허용)도 들어 있습니다. **기본 차단은 DB 파드의 들어오기도 막으므로, ③ 이 없으면 `nc … postgres 5432` 가 `open` 이 아니라 시간 초과입니다**(minikube + Calico 에서 확인). 화면 ③ 은 postgres 만 고르지만, 저장소는 Express 가 쓰는 MongoDB·Redis 도 함께 고릅니다(`matchExpressions` `app In [postgres, mongo, redis]`, 포트 5432·27017·6379). 같은 이유로 ② 에는 MongoDB·Redis 나가기 규칙과 `policyTypes`, 인그레스 포트(3000)가 더해졌습니다. `board-api.yaml` 에는 화면의 파드 spec(`/tmp` emptyDir `sizeLimit: 64Mi` 포함)에 Deployment·Service·포트·`envFrom`·프로브·자원 제한·`/app/data` 빈 볼륨이 더해졌고, 이미지 다이제스트는 예시 값(0…0)입니다. `namespace.yaml`(PSA 라벨, `warn`·`audit` 포함)과 `rbac.yaml` 의 ServiceAccount(`automountServiceAccountToken: false`)는 화면에서 명령과 설명으로만 다룹니다. 확인용 `nettest.yaml`(busybox, restricted 통과)과 거부 확인용 `debug-pod.yaml`(privileged)이 있습니다.
- 6-7: 저장소에 대응 파일이 없습니다(AWS 계정 설정은 예시 명령과 정책으로만 다룸). 화면 s7 의 신뢰 정책 sub 조건 `repo:example/board:ref:refs/heads/main` 은 `release.yml`(main 푸시, `environment` 없음)이 받는 토큰의 sub 와 같습니다. 배포 작업에 `environment:` 를 더하면 sub 는 `repo:<owner>/<repo>:environment:<이름>` 으로 바뀌므로 신뢰 정책도 함께 바꿉니다.
- 6-8: 화면의 Sigma 규칙(`detections/linux/new-systemd-service.yml`)에 `id`·`description`·`author`·`date` 가 더해졌습니다(화면 13줄 제한). 화면의 헌팅 명령은 `ops/hunting/hunt-persistence.sh` 에 묶었고, crontab 반복(`… | while read -r u`)과 삭제된 실행 파일 찾기(`find /proc … -printf '%p -> %l\n' | grep '(deleted)'`)는 화면과 같습니다. 스크립트는 root 로 실행하므로 화면의 `sudo` 가 없고, 기간은 인자(기본 14일), 인증 로그는 `tail -5` 입니다. 1-6 의 auditd 규칙(`50-server.rules`)은 `/etc/systemd/system` 을 감시하지 않으므로, Sigma 규칙이 동작하려면 `detections/linux/60-persistence.rules`(화면 s10 과 같은 `-w /etc/systemd/system -p wa -k systemd_persist`)를 `/etc/audit/rules.d/` 에 더해야 합니다. 사용자 서비스 폴더(`~/.config/systemd/user/`)는 사용자마다 감시 규칙을 따로 더해야 기록이 남습니다. `hunt-persistence.sh` 의 cron 목록은 화면 s6 과 같이 `--time-style=long-iso`(분 단위 시각)입니다.
- 6-9: `ops/ir/ir-collect.sh` 는 화면과 같은 순서·같은 파일 이름이고 마지막에 결과 폴더 경로를 출력합니다(화면 s6). 저장소에는 root 확인, 외부 저장소 마운트 확인(`IR_BASE`, 훈련용 `IR_ALLOW_LOCAL=1`), 오류 기록(`collect-errors.log` — 화면 s6 의 `ls` 목록에는 없음), avml 이 없을 때 메모리 덤프 생략, `umask 077` 이 더해졌습니다. 해시 대상은 화면과 같은 `*.txt` + `memory.lime` 입니다.
- 6-10: 화면 s4 의 두 쿼리는 `ops/sql/breach_scope.sql` 과 같은 조건(주소·앱 이름·첫 침입 시각)이고, 저장소는 psql 변수(`addr`·`since`)를 씁니다. `audit.read_events` 는 `ops/sql/audit_read_events.sql`(표 정의, pgaudit 등에서 적재)입니다. 화면의 `notice-draft.txt` 는 `docs/incident/breach-notice-template.md` 의 "이용자 통지문" 블록이고, 화면에 없는 손해배상 청구 창구·조문 번호, 분쟁조정위원회 전화번호(1833-6972, 2026-10-26 kopico.go.kr·pipc.go.kr 에서 확인), "그 밖에 대통령령으로 정하는 사항" 칸과 **사용 전 최신 법 제34조·시행령 확인** 안내가 있습니다. 회고 틀은 `docs/incident/postmortem-template.md` 입니다.
