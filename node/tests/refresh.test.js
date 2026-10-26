// 3-2 리프레시 토큰: 쓸 때마다 바뀌고(회전), 이미 쓴 토큰이 다시 오면 계열 전체를 폐기한다
import request from "supertest";
import { PASSWORDS, makeApp } from "./helpers.js";

let app;
beforeAll(async () => {
  ({ app } = await makeApp());
});

// Set-Cookie 에서 rt 값 꺼내기
const rtOf = (res) => {
  const c = (res.headers["set-cookie"] ?? []).find((v) => v.startsWith("rt="));
  return c ? c.split(";")[0].slice(3) : undefined;
};
const refresh = (rt) => request(app).post("/token/refresh").set("Cookie", `rt=${rt}`);
const loginRt = async () =>
  rtOf(await request(app).post("/login").send({ username: "alice", password: PASSWORDS.alice }));

test("리프레시 토큰은 쓸 때마다 바뀐다", async () => {
  const login = await request(app).post("/login").send({ username: "alice", password: PASSWORDS.alice });
  const cookie = login.headers["set-cookie"].find((v) => v.startsWith("rt="));
  expect(cookie).toMatch(/HttpOnly/);
  expect(cookie).toMatch(/Secure/);
  expect(cookie).toMatch(/SameSite=Strict/);

  const rt1 = rtOf(login);
  const res = await refresh(rt1);
  expect(res.status).toBe(200);
  expect(res.body.token).toBeDefined();                       // 새 15분 액세스 토큰
  const rt2 = rtOf(res);
  expect(rt2).toBeDefined();
  expect(rt2).not.toBe(rt1);
  expect((await refresh(rt2)).status).toBe(200);
});

test("이미 쓴 리프레시 토큰을 다시 쓰면 계열 전체가 401", async () => {
  const rt1 = await loginRt();
  const rt2 = rtOf(await refresh(rt1));                       // 정상 사용자가 회전
  expect((await refresh(rt1)).status).toBe(401);              // 공격자가 훔친 옛 토큰을 다시 씀 → 탈취 신호
  expect((await refresh(rt2)).status).toBe(401);              // 같은 계열의 새 토큰도 폐기됐다
  expect((await refresh("not-a-token")).status).toBe(401);
});
