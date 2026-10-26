// ⚠️ 의도적으로 취약한 실습 코드 — 운영에 쓰지 마세요
// LAB_MODE=vulnerable + HOST=127.0.0.1 일 때만 /lab 아래에 등록된다.
// 2-3 NoSQL 인젝션: 본문 객체가 그대로 조회 조건이 된다.
// 0-2 서명만 검증: 유출된 HS256 비밀키(change-me)로 만든 토큰이 그대로 통과한다.
import { Router } from "express";
import jwt from "jsonwebtoken";

export function labLoginRoutes({ labUsers, config }) {
  const router = Router();
  const SECRET = config.labJwtSecret;
  const issueToken = (user) => ({
    ok: true,
    user: user.username,
    role: user.role,
    token: jwt.sign({ sub: user.username, role: user.role }, SECRET),
  });
  const bearer = (req) => (req.get("authorization") ?? "").replace(/^Bearer\s+/i, "");

  router.post("/login", async (req, res) => {
    // 본문 객체가 그대로 조회 조건이 된다
    const { username, password } = req.body;
    const user = await labUsers.findOne({ username, password });
    if (!user) return res.sendStatus(401);
    res.json(issueToken(user));
  });

  router.get("/me", (req, res) => {
    try {
      // 서명만 맞으면 누구든 통과
      req.user = jwt.verify(bearer(req), SECRET);
    } catch {
      return res.sendStatus(401);
    }
    res.json({ user: req.user.sub, role: req.user.role });
  });

  return router;
}
