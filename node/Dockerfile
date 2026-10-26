# Express 예제 서비스 이미지 (로컬 실습 전용) — 6-5 멀티 스테이지 + distroless + 일반 사용자
# 베이스 이미지는 다이제스트로 고정한다(태그는 바뀔 수 있다). 다이제스트 갱신은 Dependabot(docker)이 제안한다.

# 1) 빌드 단계: 운영 의존성만 설치. npm 같은 빌드 도구는 이 단계에만 남는다
FROM node:24-slim@sha256:55fb5f5a527ea2c5c4a95f963e48ef90aa38c7b346054fdc8ee9957a2b55d205 AS build
# openssl: Prisma 가 실행 이미지(debian12, OpenSSL 3)와 같은 엔진을 고르도록 빌드 단계에도 둔다
RUN apt-get update \
 && apt-get install -y --no-install-recommends openssl \
 && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY package.json package-lock.json prisma.config.mjs ./
COPY prisma ./prisma
RUN npm ci --omit=dev --ignore-scripts          # 운영 의존성만, 설치 스크립트 끔
# 끈 설치 스크립트 중 꼭 필요한 것만 직접 실행: Prisma 엔진 받기 + 클라이언트 생성 (6-4 "필요한 것만 허용")
RUN npm rebuild @prisma/engines prisma \
 && npx --no-install prisma generate \
 && npm cache clean --force
COPY src ./src
COPY views ./views
COPY scripts ./scripts
COPY data/files ./data/files
RUN mkdir -p data/avatars data/lab-public data/audit data/log

# 2) 실행 단계: 셸·패키지 관리자 없음. nonroot 사용자(UID 65532)
FROM gcr.io/distroless/nodejs24-debian12:nonroot@sha256:14d42e2511532589a7c7e01a753667a74fcc96266e137e8125006b87b0c32d0a
WORKDIR /app
# 코드는 root 소유(앱 사용자가 고칠 수 없음), 쓰기가 필요한 data/ 만 앱 사용자 소유
COPY --from=build /app /app
COPY --from=build --chown=65532:65532 /app/data /app/data
# nonroot 사용자 (명시). Dockerfile 은 줄 끝 주석을 지원하지 않으므로 주석은 윗줄에
USER 65532:65532
ENV NODE_ENV=production \
    HOST=0.0.0.0 \
    PORT=3000 \
    DATA_BACKEND=db
EXPOSE 3000

# 셸이 없으므로 exec 형식으로 node 를 직접 부른다
HEALTHCHECK --interval=10s --timeout=3s --retries=5 \
  CMD ["/nodejs/bin/node", "-e", "fetch('http://127.0.0.1:3000/health').then(r=>process.exit(r.ok?0:1)).catch(()=>process.exit(1))"]

# 이미지의 ENTRYPOINT 는 node. 스키마 반영(prisma db push) → 서버 시작 (scripts/start.js)
CMD ["scripts/start.js"]
