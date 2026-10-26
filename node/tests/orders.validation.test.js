// 2-2 입력 검증의 원칙: zod 허용 목록 + strict, 타입 혼동
import request from "supertest";
import { login, makeApp } from "./helpers.js";

let app, token;
beforeAll(async () => {
  ({ app } = await makeApp({ labMode: true }));
  token = await login(app, "alice");
});

const order = (body, headers = {}) =>
  request(app).post("/api/orders").set("Authorization", `Bearer ${token}`).set(headers).send(body);

test("qty가 음수이면 400", async () => {
  const res = await order({ productId: 7, qty: -3 });
  expect(res.status).toBe(400);
});

test("모르는 필드 role이 오면 400", async () => {
  const res = await order({ productId: 7, qty: 1, role: "ADMIN" });
  expect(res.status).toBe(400);
});

test("문자열·배열이 오면 타입 오류 400", async () => {
  const res = await order({ productId: "7", qty: [2] });
  expect(res.status).toBe(400);
  expect(res.body.errors.map((e) => e.path[0])).toEqual(["productId", "qty"]);
});

test("정상 주문은 서버가 금액을 계산한다", async () => {
  const res = await order({ productId: 7, qty: 2, coupon: "WELCOME1" });
  expect(res.status).toBe(201);
  expect(res.body.total).toBe(78000);
});

test("같은 Idempotency-Key 재시도는 주문을 두 번 만들지 않는다", async () => {
  const h = { "Idempotency-Key": "order-retry-0001" };
  const a = await order({ productId: 8, qty: 1 }, h);
  const b = await order({ productId: 8, qty: 1 }, h);
  expect(b.status).toBe(201);
  expect(b.body.id).toBe(a.body.id);
  expect(b.headers["idempotent-replayed"]).toBe("true");
});

test("같은 Idempotency-Key 에 다른 본문이면 422", async () => {
  const h = { "Idempotency-Key": "order-retry-0002" };
  await order({ productId: 7, qty: 1 }, h).expect(201);
  const c = await order({ productId: 7, qty: 2 }, h); // 같은 키, 다른 본문
  expect(c.status).toBe(422);
  expect(c.body).toEqual({ error: "같은 Idempotency-Key 로 다른 요청을 보냈습니다" });
});

test("name 이 배열로 오면 400 (타입 혼동)", async () => {
  const res = await request(app)
    .get("/api/users?name=alice&name=bob")
    .set("Authorization", `Bearer ${token}`);
  expect(res.status).toBe(400);
});

describe("수정 전 코드(/lab)는 뚫린다", () => {
  test("음수 수량이 그대로 계산된다", async () => {
    const res = await request(app).post("/lab/orders").send({ productId: 7, qty: -3 });
    expect(res.body.total).toBe(-117000);
  });
  test("배열이 오면 500 TypeError", async () => {
    const res = await request(app).get("/lab/users?name=alice&name=bob");
    expect(res.status).toBe(500);
    expect(res.text).toContain("toLowerCase is not a function");
  });
});
