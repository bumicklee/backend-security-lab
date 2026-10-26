// 로그인 (2-3 NoSQL 인젝션 방어 · 3-1 해시 이전 · 3-5 계정별 실패 누적) + JWT 발급·폐기 (0-2 · 3-2 · 3-6)
import { Router } from "express";
import { z } from "zod";
import { verifyAndUpgrade } from "../lib/password.js";

// 객체가 아니라 문자열만 받는다 → {"$ne": null} 은 400
const Login = z.object({
  username: z.string().max(50),
  password: z.string().max(200),
});

// 리프레시 토큰 쿠키: JS 에서 읽을 수 없고(HttpOnly), HTTPS 로만, 다른 사이트 요청에는 실리지 않는다
export function refreshCookie(req) {
  return { httpOnly: true, secure: true, sameSite: "strict", path: `${req.baseUrl}/token` };
}

export function authRoutes({ users, stores }, auth) {
  const router = Router();
  const attempts = stores.loginAttempts;
  const { refreshTokens, issued } = stores;

  router.post("/login", async (req, res) => {
    const r = Login.safeParse(req.body); if (!r.success) return res.sendStatus(400);
    const key = r.data.username.toLowerCase();                 // 계정 기준 (존재 여부와 무관)
    const wait = await attempts.retryAfterSec(key);
    if (wait > 0) return res.set("Retry-After", String(wait)).sendStatus(429);
    // 조회도 같은 정규화 값으로 (대소문자만 바꿔 다른 계정처럼 세는 우회를 막는다)
    const user = await users.findOne({ username: key });
    // 수업 3-5 화면은 verifyPassword 로 줄였다. 실제로는 검증 + 해시 이전(3-1)을 함께 한다
    if (!user || !(await verifyAndUpgrade(users, user, r.data.password))) {
      await attempts.fail(key);                                // 5번째 실패부터 1·2·4…분, 최대 15분
      return res.sendStatus(401);                              // 계정 유무와 상관없이 같은 응답
    }
    await attempts.reset(key);
    // 3-2: 15분 액세스 토큰(본문) + 회전하는 리프레시 토큰(HttpOnly 쿠키, 새 계열)
    const rt = await refreshTokens.issue(user.username);
    res.cookie("rt", rt, refreshCookie(req));
    res.json(await auth.issueToken(user));
  });

  router.post("/logout", auth.requireAuth(), async (req, res) => {
    await auth.revoke(req.user.jti); // 발급 기록에서 지우면 서명이 맞아도 401
    res.sendStatus(204);
  });

  router.get("/me", auth.requireAuth(), (req, res) => {
    res.json({ user: req.user.sub, role: req.user.role });
  });

  // 3-6: 비밀번호 변경·"모든 기기 로그아웃" — 이 사용자의 모든 토큰을 끊는다
  router.post("/me/sessions/revoke-all",
    auth.requireAuth(), async (req, res) => {
      await issued.deleteAllFor(req.user.sub);
      await refreshTokens.revokeUser(
        req.user.sub);
      res.sendStatus(204);  // 기존 토큰은 401
    });

  return router;
}
