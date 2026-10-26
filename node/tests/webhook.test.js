// 결제 웹훅: HMAC 서명 + 타임스탬프 + 이벤트 ID 멱등 처리 (4-3)
import request from "supertest";
import { signWebhook } from "../src/routes/webhooks.js";
import { makeApp } from "./helpers.js";

let app, deps;
beforeAll(async () => {
  ({ app, deps } = await makeApp({ labMode: true }));
});
beforeEach(() => {
  deps.stores.payments.length = 0;
});

const SECRET = "whsec_lab_only_example";
const event = (id = "evt_000001") =>
  JSON.stringify({ id, type: "payment.succeeded", data: { orderId: 42, amount: 39000, cardNo: "4111-1111-1111-1111" } });
const now = () => Math.floor(Date.now() / 1000);

function send(body, { ts = now(), sig } = {}) {
  return request(app)
    .post("/api/webhooks/payment")
    .set("Content-Type", "application/json")
    .set("X-Webhook-Timestamp", String(ts))
    .set("X-Webhook-Signature", sig ?? signWebhook(SECRET, ts, body))
    .send(body);
}

test("올바른 서명은 200, 결제가 한 번 반영된다", async () => {
  const res = await send(event("evt_ok0001"));
  expect(res.status).toBe(200);
  expect(deps.stores.payments).toHaveLength(1);
});

test("잘못된 서명은 401", async () => {
  const res = await send(event("evt_bad001"), { sig: "00".repeat(32) });
  expect(res.status).toBe(401);
  expect(deps.stores.payments).toHaveLength(0);
});

test("서명 헤더가 없으면 401", async () => {
  const res = await request(app).post("/api/webhooks/payment").set("Content-Type", "application/json").send(event());
  expect(res.status).toBe(401);
});

test("서명 뒤 본문을 1바이트만 바꿔도 401", async () => {
  const ts = now();
  const body = event("evt_tamper1");
  const res = await send(body.replace("39000", "39001"), { ts, sig: signWebhook(SECRET, ts, body) });
  expect(res.status).toBe(401);
});

test("10분 지난 요청(재전송)은 서명이 맞아도 401", async () => {
  const res = await send(event("evt_old0001"), { ts: now() - 600 });
  expect(res.status).toBe(401);
});

test("같은 이벤트 ID 는 다시 와도 한 번만 반영", async () => {
  const body = event("evt_dup0001");
  await send(body).expect(200);
  const again = await send(body);
  expect(again.body).toEqual({ received: true, duplicate: true });
  expect(deps.stores.payments).toHaveLength(1);
});

test("형식이 잘못된 JSON 본문은 서명이 맞아도 400", async () => {
  const res = await send('{"id":"evt_broken1",');            // 서명은 이 깨진 본문으로 올바르게 계산
  expect(res.status).toBe(400);
  expect(deps.stores.payments).toHaveLength(0);
});

describe("수정 전 코드(/lab)는 뚫린다", () => {
  test("서명 없는 위조 웹훅이 결제로 반영된다", async () => {
    const res = await request(app).post("/lab/webhooks/payment").send(JSON.parse(event("evt_fake001")));
    expect(res.status).toBe(200);
    expect(deps.stores.payments).toHaveLength(1);
  });
});
