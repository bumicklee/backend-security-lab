# 예제 서비스(Spring) 이미지. 기본 프로필(안전한 /api/**)만 실행한다.
# lab-vulnerable 프로필은 127.0.0.1 바인딩을 강제하므로 컨테이너에서는 쓰지 않는다 (README 참고).
FROM eclipse-temurin:25-jdk AS build
WORKDIR /src
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
RUN ./gradlew --no-daemon dependencies > /dev/null
COPY src src
RUN ./gradlew --no-daemon bootJar -x test

FROM eclipse-temurin:25-jre
# /api/admin/ping 실습용 ping
RUN apt-get update \
 && apt-get install -y --no-install-recommends iputils-ping curl \
 && rm -rf /var/lib/apt/lists/*
# 루트가 아닌 사용자로 실행
RUN useradd --system --uid 10001 --home /app app \
 && mkdir -p /app/data/files /app/data/avatars \
 && chown -R app:app /app
WORKDIR /app
COPY --from=build /src/build/libs/board.jar /app/board.jar
USER app
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=40s --retries=5 \
  CMD curl -fs http://localhost:8080/health || exit 1
ENTRYPOINT ["java", "-jar", "/app/board.jar"]
