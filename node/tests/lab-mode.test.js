// 실습 모드 안전장치와 Express 보안 기본 세트(0-5 수업)
import request from "supertest";
import { assertSafeBind, describeListenError } from "../src/config.js";
import { app } from "../src/app.js";
import { makeApp } from "./helpers.js";

test("LAB_MODE=vulnerable 은 0.0.0.0 에서 시작을 거부한다", () => {
  expect(() => assertSafeBind({ labMode: true, host: "0.0.0.0" })).toThrow(/127\.0\.0\.1/);
  expect(() => assertSafeBind({ labMode: true, host: "198.51.100.10" })).toThrow();
  expect(() => assertSafeBind({ labMode: true, host: "127.0.0.1" })).not.toThrow();
  expect(() => assertSafeBind({ labMode: false, host: "0.0.0.0" })).not.toThrow();
});

test("포트가 이미 쓰이면 listen 콜백에 EADDRINUSE 가 오고, 안내 문장에 PORT=… 가 있다", async () => {
  const first = app.listen(0, "127.0.0.1");
  await new Promise((r) => first.once("listening", r));
  const { port } = first.address();
  try {
    const err = await new Promise((resolve) => {
      const second = app.listen(port, "127.0.0.1", (e) => {
        second.close();
        resolve(e);
      });
    });
    expect(err?.code).toBe("EADDRINUSE");
    const msg = describeListenError(err, { host: "127.0.0.1", port });
    expect(msg).toContain(`127.0.0.1:${port}`);
    expect(msg).toMatch(/PORT=\d+/);
  } finally {
    await new Promise((r) => first.close(r));
  }
});

test("상태 확인", async () => {
  const res = await request(app).get("/health");
  expect(res.body).toEqual({ status: "ok" });
});

test("취약 라우트는 실습 모드에서만 등록된다", async () => {
  expect((await request(app).get("/lab/posts")).status).toBe(404);
  const { app: lab } = await makeApp({ labMode: true });
  const res = await request(lab).get("/lab/posts").query({ title: "공지" });
  expect(res.status).toBe(200);
  expect(res.headers["x-lab-mode"]).toBe("vulnerable");
});

test("helmet 보안 헤더가 붙고 X-Powered-By 는 없다", async () => {
  const res = await request(app).get("/health");
  expect(res.headers["x-content-type-options"]).toBe("nosniff");
  expect(res.headers["content-security-policy"]).toBeDefined();
  expect(res.headers["x-powered-by"]).toBeUndefined();
});

test("100kb 를 넘는 JSON 본문은 413", async () => {
  const res = await request(app).post("/login").send({ username: "a", password: "x".repeat(150_000) });
  expect(res.status).toBe(413);
});

test("요청 제한 헤더가 붙는다", async () => {
  const res = await request(app).get("/health");
  expect(res.headers["ratelimit-policy"] ?? res.headers["ratelimit"]).toBeDefined();
});

test("오류 응답에 스택 트레이스가 없다", async () => {
  const res = await request(app).post("/login").set("Content-Type", "application/json").send("{bad json");
  expect(res.status).toBe(400);
  expect(res.text).not.toMatch(/at .*\.js:\d+/);
});
