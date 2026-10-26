// ⚠️ 의도적으로 취약한 실습 코드 — 운영에 쓰지 마세요
// LAB_MODE=vulnerable + HOST=127.0.0.1 일 때만 /lab 아래에 등록된다.
// 4-1 mass assignment + 과도한 데이터 노출: 받은 본문을 그대로 $set, 문서를 통째로 응답한다.
// /api/login 으로 받은 토큰을 쓰고 실제 users 컬렉션을 고친다 → 다시 로그인하면 role 이 ADMIN.
import { Router } from "express";

export function labProfileRoutes({ users }, auth) {
  const router = Router();

  router.patch("/users/me", auth.requireAuth(), async (req, res) => {
    const who = { username: req.user.sub };
    await users.updateOne(who, { $set: req.body });   // 받은 본문을 그대로 저장
    const me = await users.findOne(who);
    res.json(me);                                     // 문서를 통째로 응답
  });

  return router;
}
