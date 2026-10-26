# 예제 서비스 · Spring Boot 4

「백엔드 개발자를 위한 서버 보안 실무」 실습용 예제 서비스의 Spring 버전입니다.
게시판, 파일 업로드, 결제 웹훅, 앱 연동 API(로그인·결제 수단·장바구니)로 구성되어 있습니다.

> **안전 안내**
> - 이 저장소에는 **일부러 넣은 취약한 코드**(`board.lab` 패키지, `/lab/**` 경로)가 있습니다.
> - 취약한 코드는 `lab-vulnerable` 프로필을 켰을 때만 등록되고, 이때 서버는 **127.0.0.1에만** 바인딩됩니다.
>   `server.address`가 127.0.0.1이 아니면 `LabSafetyGuard`가 기동을 멈춥니다.
> - 내 PC 또는 호스트 전용 네트워크의 실습 VM에서만 실행하세요. 인터넷이나 공유 서버에 열지 마세요.
> - 시드 계정, 비밀번호, 웹훅 시크릿은 모두 예시 값입니다. 실제 값을 넣지 마세요.

## 구성

| 항목 | 값 |
| --- | --- |
| 런타임 | Java 25 LTS (Gradle toolchain), Spring Boot 4.1 (Spring Framework 7 · Spring Security 7 · Hibernate 7 · Jackson 3), Gradle 9.8 wrapper |
| 의존성 | Spring Web · Security · Data JPA · Validation · Actuator, Apache Tika(매직 바이트 판별), Bouncy Castle(Argon2), Security OAuth2 Client(OIDC 로그인), spring-security-webauthn + webauthn4j(패스키), RestClient 스타터(SSL 번들), AWS SDK v2 KMS, Spring Cloud AWS 4.2 Secrets Manager |
| DB | 실행: PostgreSQL / 테스트: H2 메모리 DB (Docker 필요 없음) |
| 포트 | 8080 |
| 상태 확인 | `GET /health` → `{"status":"UP"}` |
| 패키지 | `board`(게시판), `board.order`, `board.tools`, `board.upload`, `board.preview`, `board.cart`, `board.payment`, `board.webhook`, `board.user`, `board.config`, `board.support`, `board.tls`·`board.crypto`·`board.member`·`board.privacy`(섹션 5), `board.detect`·`board.address`(6-2), `board.lab`(취약 실습) |

## 실행 방법

### 1) Docker Compose (권장, 저장소 최상위에서)

```bash
docker compose up -d
curl -s localhost:8080/health      # {"status":"UP"}
```

컨테이너는 **기본 프로필(안전한 `/api/**`)만** 실행합니다. 컨테이너 안에서 `lab-vulnerable`을 켜면
127.0.0.1에만 바인딩되어 호스트에서 접속할 수 없습니다. 이는 의도된 동작입니다.

### 2) 로컬에서 직접 실행

JDK 25가 없어도 됩니다. Gradle toolchain(foojay)이 JDK 25를 자동으로 내려받습니다.
단, Gradle 자체를 실행하는 JDK는 17 이상이어야 합니다(`JAVA_HOME`).

```bash
# DB만 컨테이너로 (최상위 compose 의 db 서비스) 또는 직접:
docker run -d --name board-db -p 127.0.0.1:5432:5432 \
  -e POSTGRES_DB=board -e POSTGRES_USER=board -e POSTGRES_PASSWORD=board-EXAMPLE-only postgres:16-alpine

./gradlew bootRun                                          # 안전한 /api/** 만
./gradlew bootRun --args='--spring.profiles.active=lab-vulnerable'   # /lab/** 추가, 127.0.0.1:8080
```

JDK 17 이상이 전혀 없다면 Docker 안에서 빌드·테스트할 수 있습니다.

```bash
docker run --rm -v "$PWD":/w -w /w eclipse-temurin:25-jdk ./gradlew test
```

### 3) 테스트

```bash
./gradlew test                                  # 전체 (H2, Docker 필요 없음)
./gradlew test --tests '*PostSearchAttackTest'  # 수업 화면과 같은 단위로
./gradlew bootJar                               # build/libs/board.jar
```

## 환경 변수

| 변수 | 기본값 | 설명 |
| --- | --- | --- |
| `SERVER_PORT` | `8080` | HTTP 포트 |
| `SERVER_ADDRESS` | (전체) / lab 프로필은 `127.0.0.1` | lab 프로필에서 다른 값이면 기동 실패 |
| `SPRING_PROFILES_ACTIVE` | (없음) | `lab-vulnerable` 을 넣으면 취약 실습 경로가 켜짐 |
| `DB_HOST` / `DB_PORT` / `DB_NAME` | `localhost` / `5432` / `board` | PostgreSQL 위치 |
| `DB_USER` / `DB_PASSWORD` | `board` / `board-EXAMPLE-only` | DB 계정 (예시 값) |
| `WEBHOOK_SECRET` | `whsec_EXAMPLE_ONLY_change_me` | 결제 웹훅 HMAC 시크릿 (예시 값) |
| `PREVIEW_ALLOWED_HOSTS` | `example.com,www.example.com,news.example.com` | 링크 미리보기 허용 호스트 |
| `APP_FILES_DIR` | `./data/files` | `/api/files` 기준 폴더 (강의 화면의 `/srv/files`) |
| `APP_UPLOAD_DIR` | `./data/avatars` | 업로드 저장 폴더 (웹 루트 밖) |
| `APP_LAB_UPLOAD_DIR` | `./data/lab-public/uploads` | lab 업로드 폴더 ("웹 루트" 역할) |
| `SEED_ADMIN_PASSWORD` / `SEED_ALICE_PASSWORD` / `SEED_BOB_PASSWORD` | `admin-EXAMPLE-pw` / `alice-EXAMPLE-pw` / `bob-EXAMPLE-pw` | 시드 계정 비밀번호 (예시 값) |
| `SESSION_COOKIE_SECURE` | `true` | 세션 쿠키 `Secure` (3-2). curl 은 localhost 에는 Secure 쿠키도 보낸다 |
| `COMPROMISED_PASSWORD_CHECK` | `true` | 로그인 때 HaveIBeenPwned(해시 앞 5자리만 전송)로 유출 비밀번호 확인 (3-5). 오프라인이면 `false` |
| `WEBAUTHN_RP_ID` / `WEBAUTHN_ALLOWED_ORIGINS` | `localhost` / `http://localhost:8080` | 패스키 RP ID·허용 오리진 (3-6) |
| `RATE_LIMIT_PER_MIN` | `100` | 4-4 `RateLimitFilter`: 로그인 사용자(없으면 IP)당 분당 요청 수 (`app.rate-limit.per-minute`) |
| `CORS_ALLOWED_ORIGINS` | `https://app.example.com` | 4-5 CORS 허용 출처 (쉼표 구분, 쿠키를 쓰므로 `*` 금지) |
| `IDP_ISSUER` / `IDP_CLIENT_ID` / `IDP_SECRET` | `https://accounts.example.com` / `board-web` / (없음) | `oidc` 프로필에서만 쓰는 IdP 설정 (3-3) |
| `INTERNAL_API_URL` / `INTERNAL_CA_FILE` | `https://pay.internal.example` / `file:/etc/board/internal-ca.pem` | 5-1 `internal-api` 프로필: 사내 CA 로만 검증하는 `RestClient internalApi` (SSL 번들 `internal`) |
| `KMS_KEY_ID` | (없음) | 5-2 봉투 암호화 KMS 키 ARN·별칭. 없으면 가짜 KMS(`LocalKmsClient`, 실습 전용). 리전·자격 증명은 AWS 기본 체인 |
| `LOCAL_KMS_MASTER_SECRET` | `local-kms-master-EXAMPLE-only` | 5-2 가짜 KMS 의 마스터 키를 만드는 실습용 값 |
| `PHONE_INDEX_KEY` | `phone-index-key-EXAMPLE-only-change-me` | 5-2 블라인드 인덱스 HMAC 키 (32바이트 이상, 암호화 키와 따로) |
| `AWS_REGION` | `ap-northeast-2` | 5-3 `aws` 프로필의 Secrets Manager 리전 |
| `MEMBER_RETENTION` / `ORDER_RETENTION` | `P30D` / `P1825D` | 5-4 파기 배치: 탈퇴 회원 행 삭제 / 주문 구매자 익명화 기준 (ISO-8601 기간) |
| `EXPORT_ALERT_THRESHOLD` | `10000` | 5-5 관리자별 오늘 누적 내보내기 건수 알림 기준 |
| `SECURITY_LOG_FILE` | `./data/log/security.log` | 6-2 보안 이벤트 로그(한 줄에 JSON, 첫 필드 `ts`) — `bulk_lookup`·`bulk_block`·`authz_denied`. fail2ban(`../ops/fail2ban/`)이 읽는다. 비우면 로그(`SECURITY` 로거)로만 |

스키마는 JPA가 만들고(`ddl-auto: update`), 첫 기동 때 시드 계정 `admin`(ADMIN)·`alice`·`bob`(USER), 글 3건, 주문 1000~1010(1001 = alice 24,000원, 1002 = bob 30,000원)을 넣습니다. 4-1 부터 계정에 `nickname`·`bio`·`reset_token`(가짜 `rt_EXAMPLE_…`) 컬럼이 더해졌습니다.
5-2 부터 `members` 테이블(회원 alice·bob, 400일 전에 탈퇴한 carol — 전화번호는 `phone_ct`·`phone_iv`·`phone_key` 암호문과 `phone_idx` 블라인드 인덱스로만 저장), 5-4 부터 `privacy_access_log` 테이블과 `orders.created_at` 컬럼이 생깁니다.

> **4-4 프록시 신뢰**: `application.yml` 의 `server.forward-headers-strategy: native` + `server.tomcat.remoteip.internal-proxies: "10\\.0\\.0\\.\\d+"` 는
> "10.0.0.x 로드밸런서가 붙인 `X-Forwarded-For` 만 믿는다"는 뜻입니다. 실제 로드밸런서 주소에 맞게 바꾸고,
> **스테이징에서 `X-Forwarded-For` 를 바꿔 보내도 `getRemoteAddr()` 가 실제 클라이언트 IP 로 남는지 꼭 확인**하세요.

> **이미 만든 DB 를 쓰고 있다면** 3-1 이후 `users.password_hash` 길이가 100 → 255 로 늘었습니다(`{argon2}$argon2id$…` 는 100자를 넘습니다).
> `ddl-auto: update` 는 컬럼 길이를 바꾸지 않으므로, 데이터를 지우고 다시 만들거나(`docker compose down -v` 후 `docker compose up -d`)
> 직접 `ALTER TABLE users ALTER COLUMN password_hash TYPE varchar(255);` 를 실행하세요. 주문 시드도 이때 들어갑니다.

## 프로필

| 프로필 | 켜지는 것 | 바인딩 |
| --- | --- | --- |
| (기본) | `/api/**`, `/health` | 설정대로 (컨테이너에서는 0.0.0.0) |
| `lab-vulnerable` | 위 + `/lab/**` (`board.lab.*`) | **127.0.0.1 강제**. 아니면 `LabSafetyGuard`가 기동 중단 |
| `oidc` | 위 + OAuth 2.0·OIDC 로그인 (`application-oidc.yml`, `IDP_ISSUER`·`IDP_SECRET` 필요. 시작할 때 IdP discovery 를 읽는다) | 설정대로 |
| `internal-api` | 5-1 SSL 번들 `internal`(사내 CA) + `RestClient internalApi` (`INTERNAL_CA_FILE`·`INTERNAL_API_URL`) | 설정대로 |
| `aws` | 5-3 `spring.config.import: optional:aws-secretsmanager:prod/board-api/db`(·`/webhook`) + 5-2 실제 KMS(`KMS_KEY_ID`) | 설정대로 |
| `test` | 테스트 전용 H2 설정 (`src/test/resources/application-test.yml`, 유출 비밀번호 확인 끔) | - |

기본 프로필에서 `/lab/**` 요청은 Spring Security가 거절합니다(401/403).

## 수업 ↔ 코드 ↔ 경로 ↔ 테스트

| 수업 | 안전한 구현 (`/api`) | 취약 실습 (`/lab`, lab 프로필) | 테스트 |
| --- | --- | --- | --- |
| 0-3 로그 마스킹 | `board.support.Mask` (결제·주문 로그) | - | `MaskTest` |
| 0-5 실습 환경 | `GET /health`, `config.SecurityConfig` (CSRF·보안 헤더·세션 고정 방어 기본 ON) | - | `BoardApplicationTest` |
| 2-2 입력 검증 | `POST /api/orders` · `order.OrderRequest`(Bean Validation) · `OrderController`(@Valid) | `POST /lab/orders` · `lab.LabOrderController`(@Valid 없음) | `OrderValidationTest` |
| 2-3 SQL 인젝션 | `GET /api/posts?title=` · `PostRepository`(JPQL `:title`) / `GET /api/posts/search?keyword=&sort=&dir=` · `PostJdbcSearch`(`?` 바인딩 + ORDER BY 허용 목록) | `GET /lab/posts?title=`(JdbcTemplate 결합) · `/lab/posts/jpql?title=`(JPQL 결합) · `/lab/posts/search?sort=`(ORDER BY 결합) · `lab.LabPostController` | `PostSearchAttackTest` |
| 2-4 커맨드 인젝션 | `GET /api/admin/ping?host=` (ADMIN) · `tools.PingController`(허용 형식 + 인자 배열 + `--`) | `GET /lab/admin/ping?host=` (`sh -c`) · `lab.LabCommandPathController` | `CommandPathAttackTest` |
| 2-4 경로 조작 | `GET /api/files?name=` · `tools.FileController`(normalize + `Path.startsWith`) | `GET /lab/files?name=` (문자열 결합) | `CommandPathAttackTest` |
| 2-5 파일 업로드 | `POST /api/avatars` (form 필드 `file`) · `GET /api/avatars/{id}` · `upload.AvatarController`(Tika 매직 바이트, UUID 이름, 웹 루트 밖, attachment + nosniff) | `POST /lab/avatars` · `GET /lab/uploads/{name}` · `lab.LabAvatarController`(Content-Type만 확인, 원본 이름) | `AvatarUploadAttackTest` |
| 2-6 SSRF | `GET /api/preview?url=` · `preview.PreviewController` + `preview.UrlGuard`(https + 호스트 허용 목록 + 해석된 IP 검사 + 리다이렉트 금지 RestClient) | `GET /lab/preview?url=` · `lab.LabPreviewController` | `PreviewSsrfTest` |
| 2-7 역직렬화 | `POST /api/carts`(JSON DTO `cart.Cart`) · `POST /api/carts/legacy`(`cart.SafeObjectInput`, ObjectInputFilter 허용 목록) · `POST /api/payments`(`payment.Payment`, 이름 있는 하위 타입 card·bank) | `POST /lab/carts/legacy` (필터 없는 ObjectInputStream) · `lab.LabCartController` | `DeserializationAttackTest` |
| 2-8 공격 재현 테스트 | 위 전체 | - | `AttackRegressionTest` (+ 위 테스트들), `LabVulnerableDemoTest`(lab이 실제로 뚫리는지 대조) |
| 3-1 비밀번호 저장 | `config.SecurityConfig#passwordEncoder` (`DelegatingPasswordEncoder("argon2", {argon2: Argon2PasswordEncoder(16,32,1,19456,2), bcrypt})`) · `user.AppUserDetailsService`(`UserDetailsPasswordService`: 로그인 때 `{bcrypt}` → `{argon2}`) · `AppUser.changePasswordHash` | - | `PasswordStorageTest` |
| 3-2 세션 | `POST /api/auth/login`(세션 발급, 세션 ID 교체) · `GET /api/me` · `POST /api/auth/logout` · `GET /api/csrf` · `user.AuthController` · `application.yml`의 `server.servlet.session.cookie.*`(HttpOnly·Secure·SameSite=Lax, 30분) | - | `AuthLoginTest` (쿠키 속성은 RANDOM_PORT 실제 서버로 확인) |
| 3-3 OAuth 2.0·OIDC | `oidc` 프로필 · `GET /oauth2/authorization/idp` → `/login/oauth2/code/idp` · `SecurityConfig`의 `oauth2Login()`(등록이 있을 때만) | - | `OAuthLoginTest` (state·nonce·code_challenge(S256)·redirect_uri, 다른 state 콜백 거절) |
| 3-4 IDOR·BOLA | `GET /api/orders/{id}` · `order.OrderController`(`findByIdAndOwner`, 남의·없는 주문 403) · `GET /api/v2/orders/{id}`(`@PostAuthorize`) · `GET/DELETE /api/admin/users`(`@PreAuthorize`) · `config.MethodSecurityConfig`(`@EnableMethodSecurity`) | `GET /lab/orders/{id}` (주인 확인 없음) | `OrderAuthorizationTest`, `OrderMethodSecurityTest` |
| 3-5 무차별 대입 | `user.LoginAttempts`(계정별 5번째 실패부터 1·2·4…분, 최대 15분 → 429 + `Retry-After`) · `CompromisedPasswordChecker`(`HaveIBeenPwnedRestApiPasswordChecker`) → 403 `password_reset_required` | - | `LoginProtectionTest` (`@MockitoBean`, 네트워크 없음) |
| 3-6 MFA·세션 탈취 | `@EnableMultiFactorAuthentication`(비밀번호 + 패스키, 패스키를 등록한 사용자에게만) · `webAuthn()`(`/webauthn/register/options`, `/webauthn/register`, `/webauthn/authenticate/options`, `/login/webauthn`) · `POST /api/me/password`·`POST /api/me/sessions/revoke-all` · `user.PrincipalSessionRepository`(`findByPrincipalName`·`deleteById`) | - | `MfaSessionTest` |
| 3-7 인증·인가 테스트 | 인가 매트릭스(`@ParameterizedTest` + `@CsvSource`) + 익명 401 | - | `OrderAuthorizationTest` |
| 4-1 Mass assignment·과도한 노출 | `PATCH /api/users/me` · `user.ProfileController`·`ProfileService`(`ProfileUpdate` 요청 DTO · `ProfileView` 응답 DTO, 모르는 필드는 Jackson 이 400) | `PATCH /lab/users/me` (`@RequestBody AppUser` + `BeanUtils.copyProperties`, 엔티티 그대로 응답) · `lab.LabProfileController` | `ProfileUpdateTest`, `LabVulnerableDemoTest` |
| 4-2 XSS·CSP | `GET /api/posts/{id}/view` (`templates/post.html`, `th:text`) · `GET /api/posts/{id}/rich` (OWASP Java HTML Sanitizer `FORMATTING`+`LINKS`+`BLOCKS`, `th:utext`) · `PostViewController`, `SecurityConfig` 의 `headers().contentSecurityPolicy(...)` | `GET /lab/posts/{id}/view` (`templates/lab/post.html`, `th:utext`) · `lab.LabPostViewController` | `PostXssTest`, `RichTextSanitizerTest`, `LabVulnerableDemoTest` |
| 4-4 Rate limiting | `config.RateLimitFilter` (Bucket4j, 키 = `getUserPrincipal()` → 없으면 `getRemoteAddr()`, 429 + `Retry-After`) · `server.tomcat.remoteip.*` | - | `RateLimitFilterTest` |
| 4-5 CORS·CSRF | `SecurityConfig` 의 `csrf(c -> c.spa())`(XSRF-TOKEN 쿠키 → X-XSRF-TOKEN 헤더) · `CorsConfigurationSource`(허용 출처 + 자격 증명) · `server.servlet.session.cookie.same-site: lax` | - | `CsrfCorsTest` |
| 4-3·4-4 웹훅 | `POST /api/webhooks/payments` · `webhook.WebhookVerifier`(HMAC-SHA256 + 타임스탬프 5분) · `WebhookController`(이벤트 ID 멱등 처리) | `POST /lab/webhooks/payments` (검증 없음, 매번 반영) · `lab.LabWebhookController` | `WebhookSignatureTest` |
| 5-1 TLS·인증서 검증 | `tls.InternalApiConfig` (`internal-api` 프로필, `builder.apply(ssl.fromBundle("internal"))`, `app.internal-api.base-url` 이 있을 때만) | - | `InternalApiTlsTest` (번들이 사내 CA 하나만 신뢰, 중간자 인증서 거절, 모두 믿는 TrustManager 코드 없음) |
| 5-2 저장 데이터 암호화·KMS | `crypto.FieldCrypto` (`kms.generateDataKey(r -> r.keyId(KEY_ARN).keySpec(AES_256))` + `AES/GCM/NoPadding` + `GCMParameterSpec(128, iv)` + HMAC 블라인드 인덱스) · `crypto.CryptoConfig` · `crypto.LocalKmsClient`(가짜 KMS) · `member.MemberService` | - | `EnvelopeEncryptionTest` |
| 5-3 시크릿 관리 | `application-aws.yml` (`optional:aws-secretsmanager:prod/board-api/db`) · 저장소 최상위 `.gitleaks.toml`·`.pre-commit-config.yaml` | - | - |
| 5-4 개인정보 | `support.Mask` · `POST /api/auth/signup`(`user.SignupController.SignupRequest`: `@Email`·`@Size(min = 12)`·`@NotBlank nickname`·`@AssertTrue over14`) · `GET /api/admin/members/{id}`·`?phone=` + `privacy.PrivacyAccessInterceptor`(`@PrivacyAccess`, `privacy_access_log` 테이블, `@Immutable` + 삭제 없는 저장소) · `privacy.RetentionJob#purgeExpired`(`@Scheduled(cron = "0 30 3 * * *")`, `members.purgeBefore` · `orders.anonymizeBuyerBefore`) | - | `MaskTest`, `PrivacyDevTest` |
| 5-5 반출 감시 | `GET /api/admin/members/export?page=&fields=` · `member.AdminMemberController`(1,000건씩 `Sort.by("id").ascending()`, 컬럼 허용 목록, `ExportCounter` 누적 → `ExportAlert`) | - | `MemberExportTest` |
| 6-2 계정별 조회량 감시 | `GET /api/addresses` · `detect.BulkLookupGuard`(`HandlerInterceptor` record, 키 `lookup:<계정>:<UTC 날짜>`, 300번째 `bulk_lookup`, 1001번째 `bulk_block`, 1000 초과 429) · `detect.DetectWebConfig`(주소 경로에만 등록) · `detect.SecurityEvents`(security.log) · `detect.MemoryLookupCounter` · `detect.AuthorizationDeniedLogger`(로그인 사용자의 403 → `authz_denied`) | - | `BulkLookupGuardTest` |
| 6-5 컨테이너 | 최상위 `compose.yaml` 의 `spring` 서비스(`user: 10001`, read_only, tmpfs `/tmp`, `cap_drop: [ALL]`, `no-new-privileges`, `/app/data` 볼륨) | - | `docker compose up --wait` |
| 안전장치 | `lab.LabSafetyGuard` | - | `LabProfileGuardTest` |

## curl 예시

```bash
# 2-3: 안전한 쪽은 0건, lab 은 전부 나온다
curl -sG localhost:8080/api/posts --data-urlencode "title=' OR 1=1--"          # []
curl -sG 127.0.0.1:8080/lab/posts --data-urlencode "title=' OR 1=1--"         # lab 프로필
curl -sG 127.0.0.1:8080/lab/posts --data-urlencode \
  "title=' UNION SELECT id, email, password_hash FROM users--"

# 로그인 (CSRF 토큰 → 세션 쿠키)
curl -s -c jar localhost:8080/api/csrf
T=$(grep XSRF-TOKEN jar | awk '{print $7}')
curl -s -b jar -c jar -H "X-XSRF-TOKEN: $T" -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin-EXAMPLE-pw"}' localhost:8080/api/auth/login
curl -s -b jar -G localhost:8080/api/admin/ping --data-urlencode "host=203.0.113.10; id"   # 400

# 3-4: 자기 주문 200, 남의 주문 403 (alice 로 로그인한 jar)
curl -s -b jar localhost:8080/api/orders/1001                                   # {"id":1001,"user":"alice",...,"total":24000,...}
curl -s -o /dev/null -w "%{http_code}\n" -b jar localhost:8080/api/orders/1002   # 403
curl -s 127.0.0.1:8080/lab/orders/1002                                          # lab 프로필: bob 주문이 그대로 보인다

# 2-5: 로그인 + CSRF 가 필요한 업로드
printf 'GIF89a<%% jsp %%>' > avatar.jpg.jsp
curl -s -b jar -H "X-XSRF-TOKEN: $T" -F "file=@avatar.jpg.jsp;type=image/jpeg" localhost:8080/api/avatars  # 400

# 4-3: 서명한 웹훅 (같은 요청을 다시 보내면 duplicate)
TS=$(date +%s); B='{"id":"evt_demo1","type":"payment.succeeded","orderId":42,"amount":12000}'
SIG="sha256=$(printf '%s.%s' "$TS" "$B" | openssl dgst -sha256 -hmac 'whsec_EXAMPLE_ONLY_change_me' -hex | awk '{print $NF}')"
curl -s -H 'Content-Type: application/json' -H "X-Webhook-Timestamp: $TS" -H "X-Webhook-Signature: $SIG" \
  -d "$B" localhost:8080/api/webhooks/payments
```

```bash
# 4-1: role 을 섞으면 400 (jar 는 위에서 로그인한 세션)
curl -s -o /dev/null -w "%{http_code}\n" -b jar -H "X-XSRF-TOKEN: $T" -X PATCH localhost:8080/api/users/me \
  --json '{"nickname":"앨리스","role":"ADMIN"}'                                   # 400
# 4-2: 이스케이프된 보기 화면 + CSP
curl -s localhost:8080/api/posts/1/view | grep content
curl -sI localhost:8080/api/posts/1/view | grep -iE "^content-security|nosniff"
# 4-5: 허용하지 않은 Origin 은 CORS 헤더 없이 403
curl -si localhost:8080/api/posts -H "Origin: https://attacker.example.net" | grep -iE "^HTTP|access-control"
```

경로 조작 실습(`/lab/files?name=../../etc/passwd`)은 기준 폴더가 `./data/files`이므로,
실행 위치에 따라 `../`를 더 붙여야 할 수 있습니다.

## 강의 화면과 다른 점

- 강의 화면의 `/posts`, `/files`, `/orders` 같은 경로는 이 저장소에서 **`/api/...`(안전한 구현)**,
  **`/lab/...`(취약한 구현)** 로 나뉘어 있습니다. 테스트 코드도 `/api/...`를 호출합니다.
- 2-3의 취약 쿼리는 화면의 `SELECT *` 대신 `SELECT id, title, author`를 씁니다.
  화면 속 UNION 공격(3개 컬럼)이 그대로 재현되도록 맞춘 것입니다.
- 업로드 파라미터 이름은 curl 화면과 같은 `file`입니다(`@RequestParam("file") MultipartFile f`).
- 경로 조작 거절 상태 코드는 400입니다(2-4 화면 기준).
- Spring Boot 4(Jackson 3) 기준이라 `ObjectMapper`·`JsonMapper`는 `tools.jackson.databind` 패키지에 있습니다.
  `@JsonTypeInfo`·`@JsonSubTypes` 같은 애너테이션은 그대로 `com.fasterxml.jackson.annotation`입니다.
  테스트의 `@AutoConfigureMockMvc`는 `org.springframework.boot.webmvc.test.autoconfigure` 패키지에서 가져옵니다.
- 3-1: Spring(Bouncy Castle)은 Argon2 파라미터를 `m=19456,t=2,p=1` 순서로 적습니다(Express 의 node-argon2 는 `m=19456,p=1,t=2`).
- 3-2: 화면의 `JwtConfig`(리소스 서버 JWT 검증)는 이 저장소에 넣지 않았습니다. Spring 쪽은 서버 세션 방식만 씁니다.
- 3-3: 화면처럼 `issuer-uri` 를 쓰면 시작할 때 IdP 에 접속하므로, OIDC 설정은 `oidc` 프로필(`application-oidc.yml`)에 두었습니다.
  IdP 등록이 있을 때만 `oauth2Login()` 을 켭니다. 테스트는 엔드포인트를 직접 적은 가짜 등록을 씁니다.
- 3-4: 화면의 `@PostAuthorize` 예시는 같은 주소에 둘 수 없어 `GET /api/v2/orders/{id}` 로 두었습니다. 없는 주문도 403 입니다.
- 3-6: 화면은 `@EnableMultiFactorAuthentication(authorities = {...})` 로 모든 사용자에게 두 요소를 요구합니다.
  이 저장소는 `when = MultiFactorCondition.WEBAUTHN_REGISTERED` 를 더해 **패스키를 등록한 사용자에게만** 요구합니다(기존 실습 계정·테스트 유지).
  두 번째 요소가 없으면 `/login` 으로 보내는 대신 401 `{"error":"webauthn_required"}` 를 돌려줍니다.
  "모든 세션 끊기"는 Spring Session 의 `FindByIndexNameSessionRepository` 대신, 같은 메서드 이름을 가진 메모리 구현
  `user.PrincipalSessionRepository` 를 씁니다(서버가 여러 대면 Spring Session JDBC·Redis 로 바꿔야 합니다).
- 3-7: JUnit 6 은 매개변수 테스트 이름의 문자열 인자에 따옴표를 붙입니다. 화면처럼 `bob → /api/orders/1001 = 403` 으로 보이도록
  `@ParameterizedTest(..., quoteTextArguments = false)` 를 넣었습니다.
- 4-1: 화면의 `profiles.update(auth.getName(), u)` 는 `user.ProfileService` 입니다. 보낸 필드만 바꿉니다(null 은 그대로 둠).
  취약 실습이 `BeanUtils.copyProperties` 로 role 을 복사하려면 setter 가 필요해서 `AppUser` 에 `setNickname`·`setBio`·`setRole` 이 있습니다.
- 4-2: 화면은 보기 화면 하나에 `th:text`(이스케이프)와 정화한 `bodyHtml`(`th:utext`)을 보여 주지만, 저장소는
  `/view`(이스케이프)와 `/rich`(정화)로 나눴습니다. 정화 테스트는 `RichTextSanitizerTest` 에 따로 있습니다.
  lab 보기 화면에도 CSP 헤더가 붙으므로 브라우저에서는 인라인 이벤트 처리기가 막힙니다(응답 HTML 로 취약점을 확인).
- 4-4: 화면의 `if (!b.tryConsume(1))` 대신 `tryConsumeAndReturnRemaining(1)` 로 남은 대기 시간을 구해 `Retry-After` 를 붙입니다.
  인증이 필요한 경로는 Spring Security 가 먼저 401 을 돌려주므로, 익명 요청은 공개 경로(`GET /api/posts` 등)에서만 셉니다.
  버킷은 서버 메모리입니다(서버가 여러 대면 bucket4j-redis 등으로 공유). 테스트 프로필은 다른 테스트가 걸리지 않도록 한도를 크게 두고,
  `RateLimitFilterTest` 만 100 으로 돌립니다. `RateLimitFilterTest` 에는 "익명은 IP, 로그인 사용자는 사용자 이름" 테스트가 하나 더 있습니다.
- 4-5: `csrf(c -> c.spa())` 에서 **헤더에는 XSRF-TOKEN 쿠키 값을 그대로**, `GET /api/csrf` 본문의 `token`(BREACH 대비로 가린 값)은
  폼 파라미터(`parameterName`, 기본 `_csrf`)로 보냅니다. `CsrfCorsTest` 에는 화면의 두 테스트 외에
  "XSRF-TOKEN 쿠키 값을 X-XSRF-TOKEN 헤더로 보내면 통과한다", "허용한 Origin 에는 그 출처와 자격 증명 허용을 돌려준다"가 더 있습니다.
  허용하지 않은 Origin 의 요청은 Spring 이 403 으로 거절합니다(CORS 헤더 없음).
- 5-1: 화면의 `truststore.certificate: classpath:ca.pem` 은 `internal-api` 프로필에서 `${INTERNAL_CA_FILE:file:/etc/board/internal-ca.pem}` 입니다(인증서는 이미지에 굽지 않고 마운트). 화면의 `builder.apply(ssl.fromBundle("internal"))` 앞에 `baseUrl(...)` 이 붙고, https 가 아니면 기동하지 않습니다.
  테스트는 `src/test/resources/tls/` 의 사내 CA·사내 CA 가 서명한 인증서·같은 이름의 자체 서명(중간자) 인증서로 번들의 신뢰 관리자를 직접 확인합니다(네트워크·개인 키 없음).
- 5-2: 화면의 `save(ct, iv, dk.ciphertextBlob())` 는 `member.Member#phone(ct, iv, wrappedKey, idx)` 입니다(Java 는 GCM 태그가 `ct` 끝에 붙음). `aesKey(dk.plaintext())` 는 `plaintext().asByteArray()` 로 꺼내 쓰고 바로 0 으로 지웁니다.
  `KMS_KEY_ID` 가 없으면 `LocalKmsClient`(AWS SDK `KmsClient` 를 구현한 가짜)가 같은 요청을 받습니다. 관리자 회원 조회 화면에는 복호화한 번호도 `Mask.phone` 으로 가려서 내보냅니다.
- 5-3: 화면의 `spring.config.import: aws-secretsmanager:prod/board-api/db` 는 `aws` 프로필에만 있고 `optional:` 을 붙였습니다. 시크릿 JSON 의 키가 설정 이름이 됩니다(예: `{"spring.datasource.password": "…", "app.webhook.secret": "…"}`).
- 5-4: `Mask` 를 Express·화면과 같은 모양으로 바꿨습니다: `last4` → `****-****-****-1234`(이전 `****1234`), `email` → `a***@example.com`(이전 `a****@`), 10자리 미만 전화번호 → `***`. `DeserializationAttackTest` 의 `masked` 기대값도 바뀌었습니다.
  화면의 `SignupRequest` 에 이메일 `@NotBlank @Size(max = 50)`, 비밀번호 `@NotNull @Size(max = 200)` 이 더해졌습니다(가입 이메일을 사용자 이름으로 씀). 모르는 필드(`residentNo`·`address`)는 Jackson 이 400 으로 거절합니다.
  화면의 `purgeExpired()` 는 `RETENTION` 하나지만, 저장소는 탈퇴 회원(`MEMBER_RETENTION`, 30일)과 주문(`ORDER_RETENTION`, 5년)을 나눴고 로그에 익명화한 주문 수도 남깁니다. 접속기록은 Express 와 같은 다섯 항목 + 결과·건수를 `privacy_access_log` 에 쓰며, `/api/admin/**` URL 규칙이 먼저 거절한 요청(403)은 남지 않습니다.
- 5-5: 화면(Express)의 내보내기를 같은 규칙으로 옮겼습니다: `PageRequest.of(page, 1000, Sort.by("id").ascending())`, 컬럼 허용 목록 `id`·`nickname`·`email`·`createdAt`, 오늘 누적 건수(`ExportCounter`, 서버 메모리)가 기준을 넘으면 `ExportAlert`(기본: 경고 로그).
- 5-7: Spring 은 서버 세션 방식이라 KMS JWT 서명 예제가 없습니다(Express 에만 있음).
- 6-2: 화면의 `StringRedisTemplate redis` 자리에 같은 모양(`increment`·`expire`)의 `detect.LookupCounter` 를 두고, 기본 구현은 서버 메모리(`MemoryLookupCounter`)입니다. Redis 스타터를 넣으면 Redis 가 없을 때 `/health` 가 DOWN 이 되므로 넣지 않았습니다. 서버가 여러 대면 `redis.opsForValue().increment(key)` 구현으로 바꿉니다.
  `events.publish(event, user, ip, n)` 은 화면과 같이 조회 수를 함께 넘깁니다(security.log 의 `count`). 화면은 429 상태만 돌려주지만 저장소의 429 응답 본문은 Express 와 같은 `{"error":"review_required"}` 입니다. 날짜는 `LocalDate.now(ZoneOffset.UTC)` 대신 주입한 `Clock`(UTC)로 계산합니다.
  fail2ban 필터가 세는 `authz_denied` 는 Spring Security 의 `AuthorizationDeniedEvent`(로그인한 사용자만)에서 남깁니다. `validation_reject` 는 아직 남기는 곳이 없습니다.
- 6-5: compose 의 Spring 서비스는 화면(`board-api`, 65532)과 달리 이미지의 `app` 사용자(UID 10001)로 돌고, JVM 때문에 `mem_limit` 을 768m 로 잡았습니다. Spring 이미지는 아직 `eclipse-temurin:25-jre` 기반(셸·ping 포함, 2-4 실습용)입니다.
