// 4-5 CSRF 데모: 세션 쿠키(sid)로 인증하는 상태 변경 API.
// 브라우저가 쿠키를 자동으로 붙이므로 다른 사이트의 위조 요청이 문제가 된다.
// 방어는 create-app.js 의 출처 확인(Origin·Sec-Fetch-Site) + 쿠키 SameSite=Lax.
import { Router } from "express";
import { z } from "zod";

const Body = z.strictObject({ email: z.email().max(100) });

export function accountRoutes({ users }) {
  const router = Router();

  router.post("/account/email", async (req, res) => {
    const username = req.session?.user;     // 3-3·3-6 에서 세션 로그인한 사용자
    if (!username) return res.sendStatus(401);
    const r = Body.safeParse(req.body);
    if (!r.success) return res.sendStatus(400);
    await users.updateOne({ username }, { $set: { email: r.data.email } });
    res.json({ username, email: r.data.email });
  });

  return router;
}
