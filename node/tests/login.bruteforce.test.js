// 3-5 무차별 대입·크리덴셜 스터핑: 계정 기준 실패 누적 → 429 + Retry-After
import request from "supertest";
import { PASSWORDS, makeApp } from "./helpers.js";

let app;
beforeEach(async () => {
  ({ app } = await makeApp()); // 테스트마다 새 앱 → 실패 횟수가 섞이지 않는다
});
const tryLogin = (username, password) => request(app).post("/login").send({ username, password });

test("여섯 번째 시도부터 429와 Retry-After", async () => {
  const codes = [];
  for (let i = 0; i < 6; i++) codes.push((await tryLogin("alice", "wrong")).status);
  expect(codes).toEqual([401, 401, 401, 401, 401, 429]);
  const res = await tryLogin("alice", PASSWORDS.alice);       // 맞는 비밀번호도 기다려야 한다
  expect(res.status).toBe(429);
  expect(res.headers["retry-after"]).toBe("60");
  expect((await tryLogin("ALICE", PASSWORDS.alice)).status).toBe(429); // 대소문자 우회도 같은 계정
});

test("없는 계정도 같은 응답, 같은 대기", async () => {
  const codes = [];
  for (let i = 0; i < 6; i++) codes.push((await tryLogin("ghost-user", "wrong")).status);
  expect(codes).toEqual([401, 401, 401, 401, 401, 429]);
  expect((await tryLogin("ghost-user", "wrong")).headers["retry-after"]).toBe("60");
});

test("로그인에 성공하면 실패 횟수 초기화", async () => {
  for (let i = 0; i < 4; i++) expect((await tryLogin("bob", "wrong")).status).toBe(401);
  expect((await tryLogin("bob", PASSWORDS.bob)).status).toBe(200);
  for (let i = 0; i < 4; i++) expect((await tryLogin("bob", "wrong")).status).toBe(401); // 다시 4번까지는 401
  expect((await tryLogin("bob", PASSWORDS.bob)).status).toBe(200);
});
