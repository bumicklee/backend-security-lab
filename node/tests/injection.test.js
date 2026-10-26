// 2-3 SQL·NoSQL 인젝션: 태그드 템플릿 바인딩, 문자열만 받는 로그인
import request from "supertest";
import { makeApp } from "./helpers.js";

let app;
beforeAll(async () => {
  ({ app } = await makeApp({ labMode: true }));
});

const PAYLOADS = ["' OR 1=1--", "' UNION SELECT id, email, password_hash FROM users--"];

test("정상 제목은 1건", async () => {
  const res = await request(app).get("/posts").query({ title: "공지" });
  expect(res.status).toBe(200);
  expect(res.body).toHaveLength(1);
});

test.each(PAYLOADS)("따옴표가 섞인 제목은 0건: %s", async (title) => {
  const res = await request(app).get("/posts").query({ title });
  expect(res.status).toBe(200);
  expect(res.body).toHaveLength(0);
});

test("$ne 연산자 로그인은 400", async () => {
  const res = await request(app).post("/login").send({ username: "admin", password: { $ne: null } });
  expect(res.status).toBe(400);
});

test("$gt 연산자 로그인도 400", async () => {
  const res = await request(app).post("/login").send({ username: { $gt: "" }, password: { $gt: "" } });
  expect(res.status).toBe(400);
});

test("틀린 비밀번호는 401", async () => {
  const res = await request(app).post("/login").send({ username: "admin", password: "wrong" });
  expect(res.status).toBe(401);
});

describe("수정 전 코드(/lab)는 뚫린다", () => {
  test("' OR 1=1-- 은 전체 글을 돌려준다", async () => {
    const res = await request(app).get("/lab/posts").query({ title: PAYLOADS[0] });
    expect(res.body.length).toBeGreaterThan(1);
  });
  test("UNION 으로 회원 이메일·해시가 나온다", async () => {
    const res = await request(app).get("/lab/posts").query({ title: PAYLOADS[1] });
    expect(res.body.map((r) => r.title)).toContain("admin@example.com");
  });
  test("$ne 로 비밀번호 없이 관리자 로그인", async () => {
    const res = await request(app).post("/lab/login").send({ username: "admin", password: { $ne: null } });
    expect(res.status).toBe(200);
    expect(res.body.role).toBe("ADMIN");
  });
});
