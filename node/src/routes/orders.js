// 주문 생성 API (2-2 입력 검증: zod 허용 목록 + strictObject) + Idempotency-Key
// 주문 상세 API (3-4 객체 수준 인가: 주인 조건을 쿼리에 함께 넣는다)
import { Router } from "express";
import { z } from "zod";
import { idempotency } from "../lib/idempotency.js";

const Order = z.strictObject({
  productId: z.number().int().positive(),
  qty: z.number().int().min(1).max(100),
  coupon: z.string().regex(/^[A-Z0-9]{8}$/).optional(),
});

export function orderRoutes({ prisma, stores, logger }, auth) {
  const router = Router();

  // 금액은 클라이언트가 아니라 서버가 계산한다
  async function createOrder(user, { productId, qty, coupon }) {
    const product = await prisma.product.findUnique({ where: { id: productId } });
    if (!product) return null;
    return prisma.order.create({
      data: { userId: user.sub, productId, qty, total: product.price * qty, coupon },
    });
  }

  router.post("/orders", auth.requireAuth(), idempotency(stores.idempotency), async (req, res) => {
    const r = Order.safeParse(req.body);
    if (!r.success) return res.status(400).json({ errors: r.error.issues });
    const order = await createOrder(req.user, r.data);
    if (!order) return res.status(404).json({ error: "product not found" });
    // 5-4 요청 본문을 통째로 남기지 않고 고른 필드만 (연락처·카드번호를 받는다면 Mask.phone · Mask.last4)
    logger.info("order", { userId: req.user.sub, orderId: order.id });
    res.status(201).json(order);
  });

  router.get("/orders/:id", auth.requireAuth(),
    async (req, res) => {
      const id = Number(req.params.id);
      if (!Number.isSafeInteger(id))
        return res.sendStatus(400);
      const order = await prisma.order
        .findFirst({ where: { id,
          userId: req.user.sub } });  // 주인만
      if (!order) return res.sendStatus(403);
      res.json(order);
    });

  return router;
}
