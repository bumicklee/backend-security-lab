// 컨테이너 시작: 스키마 반영(prisma db push, 없으면 DB 생성) → 서버 시작.
// 6-5 실행 이미지(distroless)에는 셸이 없어서 "sh -c 'prisma db push && node src/server.js'" 대신 이 파일을 쓴다.
import { execFileSync } from "node:child_process";
import { createRequire } from "node:module";

const require = createRequire(import.meta.url);
const prismaCli = require.resolve("prisma/build/index.js");
execFileSync(process.execPath, [prismaCli, "db", "push"], { stdio: "inherit" });
await import("../src/server.js");
