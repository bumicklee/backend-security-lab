// 게시판 CRUD + 검색 (2-3 SQL 인젝션: 태그드 템플릿 바인딩)
import { Router } from "express";
import { z } from "zod";

const PostBody = z.strictObject({
  title: z.string().min(1).max(100),
  content: z.string().max(10_000).default(""),
});
const Id = z.coerce.number().int().positive();

export function postRoutes({ prisma }, auth) {
  const router = Router();

  router.get("/posts", async (req, res) => {
    if (req.query.title === undefined) {
      const rows = await prisma.post.findMany({ orderBy: { id: "desc" }, take: 50 });
      return res.json(rows);
    }
    const title = String(req.query.title);
    // 태그드 템플릿: ${}는 값으로 바인딩
    const rows = await prisma.$queryRaw`
      SELECT id, title, author FROM posts
      WHERE title = ${title}`;
    res.json(rows);
    // 더 간단히: prisma.post.findMany({ where: { title } })
  });

  router.get("/posts/:id", async (req, res) => {
    const id = Id.safeParse(req.params.id);
    if (!id.success) return res.sendStatus(400);
    const post = await prisma.post.findUnique({ where: { id: id.data } });
    if (!post) return res.sendStatus(404);
    res.json(post);
  });

  router.post("/posts", auth.requireAuth(), async (req, res) => {
    const r = PostBody.safeParse(req.body);
    if (!r.success) return res.status(400).json({ errors: r.error.issues });
    // 작성자는 본문이 아니라 토큰에서 가져온다
    const post = await prisma.post.create({ data: { ...r.data, author: req.user.sub } });
    res.status(201).json(post);
  });

  router.put("/posts/:id", auth.requireAuth(), async (req, res) => {
    const id = Id.safeParse(req.params.id);
    const r = PostBody.safeParse(req.body);
    if (!id.success || !r.success) return res.sendStatus(400);
    const post = await prisma.post.findUnique({ where: { id: id.data } });
    if (!post) return res.sendStatus(404);
    if (post.author !== req.user.sub) return res.sendStatus(403); // 남의 글 수정 금지 (IDOR)
    res.json(await prisma.post.update({ where: { id: id.data }, data: r.data }));
  });

  router.delete("/posts/:id", auth.requireAuth(), async (req, res) => {
    const id = Id.safeParse(req.params.id);
    if (!id.success) return res.sendStatus(400);
    const post = await prisma.post.findUnique({ where: { id: id.data } });
    if (!post) return res.sendStatus(404);
    if (post.author !== req.user.sub && req.user.role !== "ADMIN") return res.sendStatus(403);
    await prisma.post.delete({ where: { id: id.data } });
    res.sendStatus(204);
  });

  return router;
}
