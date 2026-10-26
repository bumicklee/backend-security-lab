// 3-4 IDOR·BOLA 보충: 응답 내용, 없는 번호·숫자가 아닌 번호, 수정 전 코드(/lab)와 대조
import request from "supertest";
import { PASSWORDS, login, makeApp } from "./helpers.js";

let app, token;
beforeAll(async () => {
  ({ app } = await makeApp({ labMode: true }));
  token = await login(app, "alice");
});
const get = (url, t = token) => request(app).get(url).set("Authorization", `Bearer ${t}`);

test("자기 주문은 200 (1001 alice 24000)", async () => {
  const res = await get("/orders/1001");
  expect(res.status).toBe(200);
  expect(res.body).toMatchObject({ id: 1001, userId: "alice", total: 24000 });
});

test("남의 주문·없는 주문은 403, 숫자가 아닌 번호는 400", async () => {
  expect((await get("/orders/1002")).status).toBe(403);
  expect((await get("/orders/999999")).status).toBe(403);
  expect((await get("/orders/abc")).status).toBe(400);
  expect((await get("/api/orders/1002")).status).toBe(403);
});

test("[lab] 주인 확인이 없으면 1000~1010 이 모두 200", async () => {
  const lab = await request(app).post("/lab/login").send({ username: "alice", password: PASSWORDS.alice });
  const labToken = lab.body.token;
  const bob = await get("/lab/orders/1002", labToken);
  expect(bob.body).toMatchObject({ id: 1002, userId: "bob", total: 30000 });
  for (let id = 1000; id <= 1010; id++) {
    expect((await get(`/lab/orders/${id}`, labToken)).status).toBe(200);
  }
});
