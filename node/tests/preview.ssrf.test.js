// 2-6 SSRF: 허용 목록, 해석된 IP 검사, 리다이렉트 금지
import http from "node:http";
import request from "supertest";
import { createApp } from "../src/create-app.js";
import { isPublic } from "../src/lib/net.js";
import { makeApp } from "./helpers.js";

// 가짜 DNS: 허용 호스트가 어떤 IP 로 해석될지 테스트마다 정한다
let resolved = "203.0.113.10";
const lookup = async () => [{ address: resolved, family: 4 }];

let app, upstream, upstreamUrl, internalHits = 0;
// 테스트용 "외부 사이트": https://news.example.com 요청을 로컬 서버로 돌려 보낸다
const fetchViaLocal = (u, init) => globalThis.fetch(`${upstreamUrl}${new URL(u).pathname}`, init);

beforeAll(async () => {
  upstream = http.createServer((req, res) => {
    if (req.url === "/article") {
      res.setHeader("Content-Type", "text/html");
      return res.end('<title>오늘의 뉴스</title><meta property="og:image" content="https://news.example.com/a.png">');
    }
    if (req.url === "/redirect") {
      res.writeHead(302, { Location: `${upstreamUrl}/internal` });
      return res.end();
    }
    if (req.url === "/internal") internalHits++;
    res.end("internal secret");
  });
  await new Promise((r) => upstream.listen(0, "127.0.0.1", r));
  upstreamUrl = `http://127.0.0.1:${upstream.address().port}`;
  ({ app } = await makeApp({ overrides: { lookup, fetch: fetchViaLocal } }));
});
afterAll(() => {
  upstream.closeAllConnections();
  return new Promise((r) => upstream.close(r));
});
beforeEach(() => {
  resolved = "203.0.113.10";
});

test("허용된 외부 주소는 미리보기를 만든다", async () => {
  const res = await request(app).get("/preview").query({ url: "https://news.example.com/article" });
  expect(res.status).toBe(200);
  expect(res.body).toEqual({ title: "오늘의 뉴스", image: "https://news.example.com/a.png" });
});

test.each([
  "http://169.254.169.254/latest/meta-data/",
  "https://169.254.169.254/latest/meta-data/",
  "https://2852039166/latest/meta-data/",
  "https://0xA9FEA9FE/latest/meta-data/",
  "https://[::ffff:169.254.169.254]/latest/meta-data/",
  "https://localhost:5432/",
  "file:///etc/passwd",
  "http://news.example.com/article",
  "not a url",
])("십진수·16진수·IPv6 표기 모두 400: %s", async (url) => {
  const res = await request(app).get("/preview").query({ url });
  expect(res.status).toBe(400);
});

test("내부 IP로 해석되는 호스트는 400", async () => {
  resolved = "10.0.0.5";
  const res = await request(app).get("/preview").query({ url: "https://news.example.com/article" });
  expect(res.status).toBe(400);
});

test("302로 내부를 가리키면 요청 실패", async () => {
  const res = await request(app).get("/preview").query({ url: "https://news.example.com/redirect" });
  expect(res.status).toBe(502);
  expect(internalHits).toBe(0);
});

test.each([
  ["169.254.169.254", false],
  ["::ffff:169.254.169.254", false],
  ["::ffff:a9fe:a9fe", false],
  ["127.0.0.1", false],
  ["10.1.2.3", false],
  ["172.20.0.1", false],
  ["192.168.0.10", false],
  ["::1", false],
  ["fd00::1", false],
  ["203.0.113.10", true],
])("isPublic(%s) = %s", (ip, expected) => {
  expect(isPublic(ip)).toBe(expected);
});

describe("수정 전 코드(/lab)는 뚫린다", () => {
  test("서버가 '내부' 메타데이터 주소의 자격 증명을 대신 가져온다", async () => {
    const { deps } = await makeApp();
    const lab = createApp(deps, { labMode: true });
    const server = await new Promise((r) => {
      const s = lab.listen(0, "127.0.0.1", () => r(s));
    });
    try {
      const base = `http://127.0.0.1:${server.address().port}`;
      const M = `${base}/lab/fake-metadata/latest/meta-data`;
      const res = await request(base).get("/lab/preview").query({ url: `${M}/iam/security-credentials/app-role` });
      expect(res.body.title).toContain("ASIAEXAMPLEEXAMPLE");
    } finally {
      server.closeAllConnections();
      await new Promise((r) => server.close(r));
    }
  });
});
