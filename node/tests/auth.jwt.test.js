// 0-2 서명 검증만으로는 부족하다: RS256 + 발급 기록(jti) 확인
import { generateKeyPairSync } from "node:crypto";
import jwt from "jsonwebtoken";
import request from "supertest";
import { login, makeApp } from "./helpers.js";

let app, deps;
beforeAll(async () => {
  ({ app, deps } = await makeApp({ labMode: true }));
});

const me = (token) => request(app).get("/api/me").set("Authorization", `Bearer ${token}`);

test("발급받은 토큰은 통과", async () => {
  const res = await me(await login(app, "alice"));
  expect(res.status).toBe(200);
  expect(res.body).toEqual({ user: "alice", role: "USER" });
});

test("토큰 없이는 401", async () => {
  expect((await request(app).get("/api/me")).status).toBe(401);
});

test("로그아웃한 토큰은 서명이 맞아도 401", async () => {
  const token = await login(app, "alice");
  await request(app).post("/api/logout").set("Authorization", `Bearer ${token}`).expect(204);
  expect((await me(token)).status).toBe(401);
});

test("서명키가 유출돼도 발급 기록 없는 토큰은 401", async () => {
  // 공격자가 우리 개인키로 직접 만든 관리자 토큰
  const forged = jwt.sign({ sub: "attacker", role: "ADMIN" }, deps.keys.privateKey, {
    algorithm: "RS256",
    jwtid: "not-issued-by-us",
    issuer: "https://api.example.com", // 발급자까지 맞춰도 발급 기록이 없으면 401
  });
  expect((await me(forged)).status).toBe(401);
});

test("다른 키·HS256(change-me) 토큰은 401", async () => {
  const { privateKey } = generateKeyPairSync("rsa", { modulusLength: 2048 });
  expect((await me(jwt.sign({ sub: "x" }, privateKey, { algorithm: "RS256" }))).status).toBe(401);
  expect((await me(jwt.sign({ sub: "x", role: "ADMIN" }, "change-me"))).status).toBe(401);
});

describe("수정 전 코드(/lab)는 뚫린다", () => {
  test("유출된 비밀키로 만든 관리자 토큰이 그대로 통과", async () => {
    const forged = jwt.sign({ sub: "attacker", role: "ADMIN" }, "change-me");
    const res = await request(app).get("/lab/me").set("Authorization", `Bearer ${forged}`);
    expect(res.status).toBe(200);
    expect(res.body.role).toBe("ADMIN");
  });
});
