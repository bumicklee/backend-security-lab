// ⚠️ 의도적으로 취약한 실습 코드 — 운영에 쓰지 마세요
// LAB_MODE=vulnerable + HOST=127.0.0.1 일 때만 /lab 아래에 등록된다.
// 결제 웹훅 위조: 서명·타임스탬프·이벤트 ID 를 확인하지 않는다.
import { Router } from "express";

export function labWebhookRoutes({ stores }) {
  const router = Router();

  router.post("/webhooks/payment", (req, res) => {
    // 누가 보냈는지 확인하지 않고 본문을 믿는다
    stores.payments.push({ lab: true, ...req.body?.data });
    res.json({ received: true });
  });

  return router;
}
