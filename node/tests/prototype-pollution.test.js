// 2-7 프로토타입 오염: strict 스키마로 받을 키를 고정한다
import request from "supertest";
import { safeMerge } from "../src/lib/safe-merge.js";
import { login, makeApp } from "./helpers.js";

let app, token;
beforeAll(async () => {
  ({ app } = await makeApp({ labMode: true }));
  token = await login(app, "alice");
});

// 주의: JS 객체 리터럴의 __proto__ 는 원형을 바꾸므로, 공격 본문은 JSON 문자열로 보낸다
const patch = (path, json) =>
  request(app)
    .patch(path)
    .set("Authorization", `Bearer ${token}`)
    .set("Content-Type", "application/json")
    .send(json);

test("__proto__ 키가 있으면 400", async () => {
  const res = await patch("/me/settings", '{"__proto__":{"isAdmin":true}}');
  expect(res.status).toBe(400);
});

test("constructor.prototype 경로도 400", async () => {
  const res = await patch("/me/settings", '{"constructor":{"prototype":{"isAdmin":true}}}');
  expect(res.status).toBe(400);
});

test("요청 뒤에도 ({}).isAdmin 은 undefined", async () => {
  await patch("/me/settings", '{"__proto__":{"isAdmin":true}}');
  expect({}.isAdmin).toBeUndefined();
});

test("허용된 키는 바뀐다", async () => {
  const res = await patch("/me/settings", '{"theme":"dark"}');
  expect(res.status).toBe(200);
  expect(res.body).toEqual({ theme: "dark", lang: "ko" });
});

test("safeMerge 는 __proto__ 를 건너뛴다", () => {
  safeMerge({}, JSON.parse('{"__proto__":{"isAdmin":true},"a":{"b":1}}'));
  expect({}.isAdmin).toBeUndefined();
});

describe("수정 전 코드(/lab)는 뚫린다", () => {
  afterEach(async () => {
    await request(app).post("/lab/reset-prototype"); // 오염 정리
  });

  test("__proto__ 병합 한 번으로 모든 객체가 관리자가 된다", async () => {
    expect((await request(app).get("/lab/admin/users")).status).toBe(403);
    await request(app)
      .patch("/lab/me/settings")
      .set("Content-Type", "application/json")
      .send('{"__proto__":{"isAdmin":true}}');
    expect({}.isAdmin).toBe(true);
    expect((await request(app).get("/lab/admin/users")).status).toBe(200);
  });
});
