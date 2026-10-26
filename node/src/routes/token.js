// 3-2 리프레시 토큰 회전 + 재사용 탐지. 리프레시 토큰은 HttpOnly 쿠키(rt)로만 오간다.
import { Router } from "express";
import { refreshCookie } from "./auth.js";

export function tokenRoutes({ users, stores }, auth) {
  const router = Router();
  const { refreshTokens } = stores;
  const sha256 = refreshTokens.sha256;

  router.post("/token/refresh", async (req, res) => {
    // 원자적으로 '사용됨' 표시 후, 표시하기 전 상태를 돌려준다 (저장은 해시로)
    const rec = await refreshTokens.consume(sha256(req.cookies.rt ?? ""));
    if (!rec || rec.expiresAt < Date.now()) return res.sendStatus(401);
    if (rec.usedAt) {                                   // 이미 쓴 토큰이 또 왔다 = 탈취 신호
      await refreshTokens.revokeFamily(rec.family);     // 같은 계열 전부 폐기
      return res.sendStatus(401);
    }
    const user = await users.findOne({ username: rec.userId });
    if (!user) return res.sendStatus(401);
    const rt = await refreshTokens.issue(rec.userId, rec.family);
    res.cookie("rt", rt, refreshCookie(req));
    res.json(await auth.issueToken(user));              // 새 15분 토큰
  });

  return router;
}
