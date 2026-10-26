// semgrep --test 용 예시 (6-3 PR #42 의 검색 라우트). 실제 앱 코드가 아니다 (.semgrepignore 로 스캔 제외)
import { Router } from "express";
export const search = Router();
search.get("/search", async (req, res) => {
  const q = String(req.query.q ?? "");
  // ruleid: board-no-concat-query
  const rows = await db.query("SELECT id, title FROM posts WHERE title LIKE '%" + q + "%'");
  // ok: board-no-concat-query
  const safe = await db.query("SELECT id, title FROM posts WHERE title LIKE ?", [`%${q}%`]);
  res.json([...rows, ...safe]);
});
