// 섹션 4 대조 테스트: 수정 전 코드(/lab)는 뚫린다 (LAB_MODE=vulnerable 에서만 등록)
import request from "supertest";
import { PASSWORDS, login, makeApp } from "./helpers.js";

let app;
beforeAll(async () => {
  ({ app } = await makeApp({ labMode: true }));
});

test("[4-1] role 이 그대로 저장되고 응답에 해시·재설정 토큰이 나간다", async () => {
  const t = await login(app, "bob");
  const res = await request(app).patch("/lab/users/me").set("Authorization", `Bearer ${t}`)
    .send({ nickname: "밥", role: "ADMIN" });
  expect(res.body).toMatchObject({ username: "bob", nickname: "밥", role: "ADMIN" });
  expect(res.body.passwordHash).toBeDefined();
  expect(res.body.resetToken).toMatch(/^rt_EXAMPLE/);
  const again = await request(app).post("/api/login").send({ username: "bob", password: PASSWORDS.bob });
  expect(again.body.role).toBe("ADMIN");                       // 다시 로그인하면 관리자 토큰
});

test("[4-2] 저장된 태그가 그대로 출력되고 CSP 도 없다", async () => {
  const res = await request(app).get("/lab/posts/77/view");
  expect(res.text).toContain('<div class="content"><img src=x onerror=alert(document.cookie)></div>');
  expect(res.headers["content-security-policy"]).toBeUndefined();
});
