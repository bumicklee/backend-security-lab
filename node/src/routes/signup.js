// 5-4 최소 수집: 게시판 가입에 필요한 것만 받는다. 모르는 필드(주민등록번호·주소 …)가 오면 400
// 주소는 첫 배송 때 받고, 주민등록번호는 법령 근거가 없으면 받지 않는다.
import { Router } from "express";
import { z } from "zod";
import { hashPassword } from "../lib/password.js";

const SignupRequest = z.strictObject({
  email: z.email().max(50),
  password: z.string().min(12).max(200),
  nickname: z.string().trim().min(1).max(30),
  over14: z.literal(true),           // 만 14세 미만은 법정대리인 동의가 따로 필요
});

export function signupRoutes({ users }) {
  const router = Router();

  router.post("/signup", async (req, res) => {
    const r = SignupRequest.safeParse(req.body);
    if (!r.success) return res.status(400).json({ errors: r.error.issues.map((i) => ({ path: i.path, code: i.code })) });
    const username = r.data.email.toLowerCase();
    if (await users.findOne({ username })) return res.sendStatus(409);
    await users.insertOne({
      username, email: username, role: "USER", nickname: r.data.nickname, bio: "",
      passwordHash: await hashPassword(r.data.password) });
    res.status(201).json({ username, nickname: r.data.nickname });
  });

  return router;
}
