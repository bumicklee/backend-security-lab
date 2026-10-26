// 6-2 내 배송지 조회. 개인정보를 돌려주는 경로라 인증 뒤에 계정별 조회량 감시(bulkLookupGuard)를 붙인다.
// 화면: app.use("/api/addresses", requireAuth, bulkLookupGuard(redis, events))
import { Router } from "express";
import { bulkLookupGuard } from "../middleware/bulk-lookup.js";

// 실습용 가짜 배송지 (메모리)
const ADDRESSES = {
  alice: [{ id: 1, label: "집", address: "서울시 중구 세종대로 110 (예시)" }],
  bob: [{ id: 2, label: "회사", address: "부산시 해운대구 센텀중앙로 79 (예시)" }],
};

export function addressRoutes({ lookupCounter, securityEvents }, auth) {
  const router = Router();
  router.get("/addresses", auth.requireAuth(), bulkLookupGuard(lookupCounter, securityEvents), (req, res) => {
    res.json(ADDRESSES[req.user.sub] ?? []);
  });
  return router;
}
