// ⚠️ 의도적으로 취약한 실습 코드 — 운영에 쓰지 마세요
// LAB_MODE=vulnerable + HOST=127.0.0.1 일 때만 /lab 아래에 등록된다.
// 2-2 타입 혼동: name=alice&name=bob 이면 배열이 와서 500 이 난다.
import { Router } from "express";

export function labUserRoutes({ users }) {
  const router = Router();

  router.get("/users", async (req, res) => {
    const name = req.query.name;
    const user = await users.findOne({ username: name.toLowerCase() });
    res.json(user ? [{ username: user.username }] : []);
  });

  return router;
}
