// 3-4·3-7 인가 매트릭스: 누가 × 무엇을 × 어떤 결과로
import request from "supertest";
import { login, makeApp } from "./helpers.js";
let app; beforeAll(async () => { ({ app } = await makeApp()); });
test.each([
  ["alice", "/orders/1001", 200], ["bob", "/orders/1001", 403],
  ["alice", "/admin/users", 403], ["admin", "/admin/users", 200],
])("%s → GET %s = %i", async (user, url, expected) => {
  const token = await login(app, user);                       // 실제 로그인으로 토큰 발급
  const res = await request(app).get(url).set("Authorization", `Bearer ${token}`);
  expect(res.status).toBe(expected);
});
test("토큰 없이는 401", async () =>
  expect((await request(app).get("/orders/1001")).status).toBe(401));
