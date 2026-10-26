// 2-5 파일 업로드: 매직 바이트, 새 이름, 웹 루트 밖 저장, attachment + nosniff
import { readdirSync } from "node:fs";
import request from "supertest";
import { PNG_1x1, login, makeApp } from "./helpers.js";

let app, deps, token;
beforeAll(async () => {
  ({ app, deps } = await makeApp({ labMode: true }));
  token = await login(app, "alice");
});

const upload = (buf, filename, contentType) =>
  request(app)
    .post("/avatars")
    .set("Authorization", `Bearer ${token}`)
    .attach("file", buf, { filename, contentType });

test("SVG·HTML 업로드는 400", async () => {
  const svg = Buffer.from('<svg xmlns="http://www.w3.org/2000/svg"><script>alert(1)</script></svg>');
  const html = Buffer.from("<html><script>alert(1)</script></html>");
  expect((await upload(svg, "a.svg", "image/svg+xml")).status).toBe(400);
  expect((await upload(html, "a.png", "image/png")).status).toBe(400);
});

test("GIF 헤더를 붙인 JSP 는 400 (MIME 헤더를 믿지 않는다)", async () => {
  const fake = Buffer.concat([Buffer.from("GIF89a"), Buffer.from('<%@ page import="java.io.*" %>')]);
  expect((await upload(fake, "avatar.jpg.jsp", "image/jpeg")).status).toBe(400);
});

test("2MB 를 넘는 파일은 413", async () => {
  const big = Buffer.concat([PNG_1x1, Buffer.alloc(2_100_000)]);
  expect((await upload(big, "big.png", "image/png")).status).toBe(413);
});

test("저장 이름에 원본 이름이 쓰이지 않는다", async () => {
  const res = await upload(PNG_1x1, "../../evil.png", "image/png");
  expect(res.status).toBe(201);
  expect(res.body.id).toMatch(/^[0-9a-f-]{36}\.png$/);
  expect(readdirSync(deps.config.storeDir)).toContain(res.body.id);
});

test("다운로드 응답은 attachment + nosniff", async () => {
  const { body } = await upload(PNG_1x1, "me.png", "image/png");
  const res = await request(app).get(`/avatars/${body.id}`);
  expect(res.status).toBe(200);
  expect(res.headers["content-disposition"]).toMatch(/^attachment/);
  expect(res.headers["x-content-type-options"]).toBe("nosniff");
});

test("저장 폴더는 정적 경로로 열려 있지 않다", async () => {
  const { body } = await upload(PNG_1x1, "me.png", "image/png");
  expect((await request(app).get(`/uploads/${body.id}`)).status).toBe(404);
});

describe("수정 전 코드(/lab)는 뚫린다", () => {
  test("image/png 라고 적은 HTML 이 원본 이름 그대로 웹 루트에서 열린다", async () => {
    const html = Buffer.from("<html><body>lab stored file</body></html>");
    const up = await request(app).post("/lab/avatars").attach("file", html, { filename: "avatar.html", contentType: "image/png" });
    expect(up.body.url).toBe("/lab/uploads/avatar.html");
    const res = await request(app).get(up.body.url);
    expect(res.headers["content-type"]).toMatch(/text\/html/);
  });
});
