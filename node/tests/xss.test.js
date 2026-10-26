// 4-2 XSS 와 CSP: 출력 이스케이프 · 허용 목록 정화 · 요청별 nonce
import request from "supertest";
import { login, makeApp } from "./helpers.js";

let app;
beforeAll(async () => {
  ({ app } = await makeApp());
});

const PAYLOAD = "<img src=x onerror=alert(document.cookie)>";

test("저장된 태그는 글자로 출력된다", async () => {
  const res = await request(app).get("/posts/77/view");          // 시드: 공격자가 저장해 둔 글
  expect(res.status).toBe(200);
  expect(res.headers["content-type"]).toMatch(/^text\/html/);
  expect(res.text).toContain('<div class="content">&lt;img src=x onerror=alert(document.cookie)&gt;</div>');
  expect(res.text).not.toContain(PAYLOAD);
});

test("허용 목록 밖 태그·속성은 정화된다", async () => {
  const token = await login(app, "bob");
  const created = await request(app).post("/posts").set("Authorization", `Bearer ${token}`).send({
    title: "서식",
    content: '<p onclick="steal()"><b>굵게</b><script>alert(1)</script><a href="javascript:alert(1)">링크</a></p>' + PAYLOAD,
  });
  const res = await request(app).get(`/posts/${created.body.id}/rich`);
  expect(res.text).toContain("<p><b>굵게</b><a>링크</a></p>");
  expect(res.text).not.toMatch(/<script>alert|onclick|onerror|javascript:/);
});

test("script-src의 nonce가 요청마다 다르다", async () => {
  const nonceOf = async () => {
    const res = await request(app).get("/posts/77/view");
    const csp = res.headers["content-security-policy"];
    const m = /script-src 'self' 'nonce-([^']+)'/.exec(csp);
    expect(res.text).toContain(`<script nonce="${m[1]}">`);    // 화면의 인라인 스크립트에 같은 nonce
    expect(res.headers["x-content-type-options"]).toBe("nosniff");
    return m[1];
  };
  expect(await nonceOf()).not.toBe(await nonceOf());
});
