// ⚠️ 의도적으로 취약한 실습 코드 — 운영에 쓰지 마세요
// LAB_MODE=vulnerable + HOST=127.0.0.1 일 때만 /lab 아래에 등록된다.
// 2-2 입력 검증 없음: qty 가 음수·배열이어도 그대로 계산한다.
// 3-4 IDOR·BOLA: 로그인만 확인하고 주인은 확인하지 않는다 → 번호만 바꾸면 남의 주문이 보인다.
import { Router } from "express";
import jwt from "jsonwebtoken";

export function labOrderRoutes({ prisma, config }) {
  const router = Router();

  // /lab/login 이 발급한 토큰으로 "로그인했는가"만 본다
  const requireLabAuth = (req, res, next) => {
    try {
      req.user = jwt.verify((req.get("authorization") ?? "").replace(/^Bearer\s+/i, ""), config.labJwtSecret);
      next();
    } catch {
      res.sendStatus(401);
    }
  };

  router.post("/orders", async (req, res) => {
    const { productId, qty, coupon } = req.body;
    const product = await prisma.product.findUnique({ where: { id: productId } });
    if (!product) return res.sendStatus(404);
    const total = product.price * qty; // qty가 음수라면?
    res.status(201).json({ productId, qty, coupon, total });
  });

  router.get("/orders/:id", requireLabAuth,
    async (req, res) => {
      const id = Number(req.params.id);
      const order = await prisma.order
        .findUnique({ where: { id } });   // 주인 조건이 없다
      if (!order) return res.sendStatus(404);
      res.json(order);
    });

  return router;
}
