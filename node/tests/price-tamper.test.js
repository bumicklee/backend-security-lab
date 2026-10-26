// 4-1 모바일 앱 검증을 믿지 않는다: 가격·쿠폰 자격은 서버가 다시 계산·판정한다
import request from "supertest";
import { login, makeApp } from "./helpers.js";

let app, token;
beforeAll(async () => {
  ({ app } = await makeApp());
  token = await login(app, "alice");
});

const order = (body) => request(app).post("/orders").set("Authorization", `Bearer ${token}`).send(body);

test("가격·쿠폰 자격 필드를 보내면 400 (unrecognized_keys)", async () => {
  const res = await order({ productId: 7, qty: 1, price: 100, couponOk: true });
  expect(res.status).toBe(400);
  expect(res.body.errors[0]).toMatchObject({ code: "unrecognized_keys", keys: ["price", "couponOk"] });
});

test("금액은 서버가 가진 가격으로 계산한다 (39,000원)", async () => {
  const res = await order({ productId: 7, qty: 1 });
  expect(res.status).toBe(201);
  expect(res.body).toMatchObject({ userId: "alice", productId: 7, qty: 1, total: 39000, coupon: null });
});
