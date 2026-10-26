// 결제 웹훅 (4-3): HMAC 서명 + 타임스탬프 + 이벤트 ID 멱등 처리
import { createHmac, timingSafeEqual } from "node:crypto";
import express, { Router } from "express";
import { z } from "zod";
import { Mask } from "../lib/mask.js";

const Event = z.object({
  id: z.string().regex(/^evt_[A-Za-z0-9]{6,64}$/),
  type: z.enum(["payment.succeeded", "payment.failed"]),
  data: z.object({
    orderId: z.number().int().positive(),
    amount: z.number().int().nonnegative(),
    cardNo: z.string().max(19).optional(),
  }),
});

// 서명 = HMAC-SHA256(secret, `${timestamp}.${원본 바디}`) 의 hex
export function signWebhook(secret, timestamp, rawBody) {
  return createHmac("sha256", secret).update(`${timestamp}.`).update(rawBody).digest("hex");
}

export function webhookRoutes({ config, stores, logger }) {
  const router = Router();
  const EVENT_TTL_SEC = 7 * 24 * 60 * 60;

  // express.json 보다 먼저 등록: 서명은 "파싱 전 원본 바이트"로 계산해야 한다
  router.post("/webhooks/payment", express.raw({ type: "application/json", limit: "64kb" }), (req, res) => {
    const ts = req.get("X-Webhook-Timestamp");
    const sig = req.get("X-Webhook-Signature");
    if (!Buffer.isBuffer(req.body) || !ts || !sig) return res.sendStatus(401);

    // 1) 오래된 요청(재전송)은 거절
    const age = Math.abs(Date.now() / 1000 - Number(ts));
    if (!Number.isFinite(age) || age > config.webhookToleranceSec) return res.sendStatus(401);

    // 2) 서명 비교는 상수 시간으로
    const expected = Buffer.from(signWebhook(config.webhookSecret, ts, req.body), "hex");
    const given = Buffer.from(String(sig), "hex");
    if (given.length !== expected.length || !timingSafeEqual(given, expected)) {
      return res.sendStatus(401);
    }

    // 3) 서명이 맞은 뒤에 파싱(형식이 깨졌으면 400)·스키마 검증
    let event; try { event = JSON.parse(req.body); } catch { return res.sendStatus(400); }
    const parsed = Event.safeParse(event); if (!parsed.success) return res.sendStatus(400);
    const { data } = parsed;

    // 4) 같은 이벤트 ID 는 한 번만 반영 (결제사는 최소 한 번 이상 보낸다)
    if (!stores.webhookEvents.setIfAbsent(data.id, true, EVENT_TTL_SEC)) {
      return res.json({ received: true, duplicate: true });
    }
    stores.payments.push({ eventId: data.id, type: data.type, ...data.data, cardNo: undefined });
    logger.info("payment webhook", {
      eventId: data.id,
      orderId: data.data.orderId,
      card: data.data.cardNo ? Mask.last4(data.data.cardNo) : null, // 카드번호는 마스킹
    });
    res.json({ received: true });
  });

  return router;
}
