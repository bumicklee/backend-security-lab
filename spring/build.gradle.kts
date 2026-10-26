plugins {
    java
    id("org.springframework.boot") version "4.1.2"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "lab.backendsecurity"
version = "0.1.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

// 6-1 의존성 잠금: 해석된 버전 전체를 gradle.lockfile 에 고정한다 → osv-scanner·trivy fs 가 이 파일로 Spring 의존성을 본다.
// 의존성을 바꾼 뒤에는 ./gradlew dependencies --write-locks 로 다시 만든다
dependencyLocking {
    lockAllConfigurations()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    // 업로드 파일의 실제 형식을 매직 바이트로 판별 (2-5)
    implementation("org.apache.tika:tika-core:4.1.0")
    // 3-1 Argon2PasswordEncoder 가 쓰는 Argon2 구현 (Bouncy Castle)
    implementation("org.bouncycastle:bcprov-jdk18on:1.86")
    // 3-3 OAuth 2.0 · OIDC 로그인 (Boot 4 스타터 이름)
    implementation("org.springframework.boot:spring-boot-starter-security-oauth2-client")
    // 3-6 패스키(WebAuthn) — Spring Security 의 webAuthn() 이 webauthn4j 로 서명을 검증한다
    implementation("com.webauthn4j:webauthn4j-core:0.31.10.RELEASE")
    implementation("org.springframework.security:spring-security-webauthn")
    // 4-2 서버 렌더링 화면(th:text 자동 이스케이프) + 서식 있는 본문 허용 목록 정화
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    implementation("com.googlecode.owasp-java-html-sanitizer:owasp-java-html-sanitizer:20260924.1")
    // 4-4 요청 제한 (토큰 버킷)
    implementation("com.bucket4j:bucket4j_jdk17-core:8.21.0")
    // 5-1 내부 API 클라이언트: SSL 번들(spring.ssl.bundle.*) → RestClientSsl
    implementation("org.springframework.boot:spring-boot-starter-restclient")
    // 5-2 KMS 데이터 키(봉투 암호화) · 5-3 spring.config.import: aws-secretsmanager: (aws 프로필에서만)
    implementation(platform("io.awspring.cloud:spring-cloud-aws-dependencies:4.2.0"))
    implementation("io.awspring.cloud:spring-cloud-aws-starter-secrets-manager")
    implementation("software.amazon.awssdk:kms")

    runtimeOnly("org.postgresql:postgresql")

    // Boot 4: 테스트 지원도 기술별 모듈로 나뉘었다 (MockMvc → webmvc-test, 보안 → security-test)
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testRuntimeOnly("com.h2database:h2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        // 강의 화면처럼 "클래스 > 테스트 이름 PASSED" 형태로 출력
        events("passed", "failed", "skipped")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.SHORT
    }
}

tasks.bootJar {
    archiveFileName = "board.jar"
}

tasks.jar {
    enabled = false
}
