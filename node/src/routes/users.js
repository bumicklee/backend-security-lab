// 회원 조회 (2-2 타입 혼동: 쿼리스트링 배열을 스키마로 막는다) + 관리자 목록
// 4-1 내 프로필 수정: 받을 필드(z.strictObject)와 내보낼 필드(view)를 정한다
import { Router } from "express";
import { z } from "zod";

const Query = z.object({ name: z.string().min(1).max(50) });

// 4-1: 바꿀 수 있는 필드만. role·passwordHash·resetToken 같은 모르는 필드가 오면 400
const Patch = z.strictObject({
  nickname: z.string().max(30).optional(),
  bio: z.string().max(200).optional() });
// 응답은 필드를 골라 새 객체로 (문서에 필드가 늘어도 응답은 그대로)
const view = ({ username, nickname, bio }) =>
  ({ username, nickname, bio });

export function userRoutes({ users }, auth) {
  const router = Router();

  router.get("/users", auth.requireAuth(), async (req, res) => {
    const r = Query.safeParse(req.query); // name=a&name=b 처럼 배열이 오면 400
    if (!r.success) return res.status(400).json({ errors: r.error.issues });
    const user = await users.findOne({ username: r.data.name.toLowerCase() });
    res.json(user ? [{ username: user.username }] : []);
  });

  router.patch("/users/me", auth.requireAuth(), async (req, res) => {
    const who = { username: req.user.sub };
    const r = Patch.safeParse(req.body);
    if (!r.success) return res.sendStatus(400);
    await users.updateOne(who, { $set: r.data });
    res.json(view(await users.findOne(who)));
  });

  router.get("/admin/users", auth.requireAuth(), auth.requireRole("ADMIN"), async (req, res) => {
    const list = await users.find({}).toArray();
    // 비밀번호 해시 같은 내부 필드는 내보내지 않는다
    res.json(list.map(({ username, email, role }) => ({ username, email, role })));
  });

  return router;
}
