// Prisma 7 설정: 접속 주소는 schema가 아니라 여기서 환경 변수로 읽는다
import { defineConfig } from "prisma/config";

export default defineConfig({
  schema: "prisma/schema.prisma",
  datasource: {
    url: process.env.DATABASE_URL ?? "postgresql://lab:lab-only-password@localhost:5432/express_board",
  },
});
