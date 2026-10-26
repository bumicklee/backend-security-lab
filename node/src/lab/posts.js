// ⚠️ 의도적으로 취약한 실습 코드 — 운영에 쓰지 마세요
// LAB_MODE=vulnerable + HOST=127.0.0.1 일 때만 /lab 아래에 등록된다.
// 2-3 SQL 인젝션: $queryRawUnsafe 에 입력을 문자열로 이어 붙인다.
import { Router } from "express";

export function labPostRoutes({ prisma }) {
  const router = Router();

  router.get("/posts", async (req, res) => {
    const { title } = req.query;
    const rows = await prisma
      .$queryRawUnsafe(
        `SELECT id, title, author FROM posts
       WHERE title = '${title}'`);
    res.json(rows);
  });

  return router;
}
