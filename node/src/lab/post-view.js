// ⚠️ 의도적으로 취약한 실습 코드 — 운영에 쓰지 마세요
// LAB_MODE=vulnerable + HOST=127.0.0.1 일 때만 /lab 아래에 등록된다.
// 4-2 저장형 XSS: 저장된 글을 템플릿 문자열로 그대로 이어 붙인다.
import { Router } from "express";

export function labPostViewRoutes({ prisma }) {
  const router = Router();

  router.get("/posts/:id/view", async (req, res) => {
    const post = await prisma.post.findUnique({ where: { id: Number(req.params.id) } });
    if (!post) return res.sendStatus(404);
    res.removeHeader("Content-Security-Policy"); // "수정 전 서버"처럼 CSP 도 없앤다
    res.send(`<h1>${post.title}</h1>
    <div class="content">${post.content}</div>`);   // 그대로 이어 붙임
  });

  return router;
}
