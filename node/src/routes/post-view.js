// 4-2 게시글 보기 화면 (HTML). 출력은 템플릿 엔진의 기본 이스케이프(<%= %>)로.
// CSP(요청별 nonce)·nosniff 는 create-app.js 의 helmet 이 붙인다.
import { Router } from "express";
import { z } from "zod";
import { sanitizeRich } from "../lib/sanitize-html.js";

const Id = z.coerce.number().int().positive();

export function postViewRoutes({ prisma }) {
  const router = Router();

  async function load(req, res) {
    const id = Id.safeParse(req.params.id);
    if (!id.success) { res.sendStatus(400); return null; }
    const post = await prisma.post.findUnique({ where: { id: id.data } });
    if (!post) { res.sendStatus(404); return null; }
    return post;
  }

  router.get("/posts/:id/view", async (req, res) => {
    const post = await load(req, res);
    if (!post) return;
    res.render("post", { post });   // views/post.ejs: <%= %>는 이스케이프
  });

  // 서식 있는 본문: 허용 목록으로 정화한 HTML 만 그대로 출력
  router.get("/posts/:id/rich", async (req, res) => {
    const post = await load(req, res);
    if (!post) return;
    const bodyHtml = sanitizeRich(post.content);
    res.render("post-rich", { post, bodyHtml });
  });

  return router;
}
