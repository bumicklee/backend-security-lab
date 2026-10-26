// 5-1 외부 호출의 인증서 검증: 끄지 말고, 내부 API 클라이언트에만 사내 CA 를 지정한다
import { X509Certificate } from "node:crypto";
import { readdirSync, readFileSync, statSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { createInternalAgent, createInternalApi } from "../src/clients/internal-api.js";

const HERE = path.dirname(fileURLToPath(import.meta.url));
const fixture = (name) => readFileSync(path.join(HERE, "fixtures/tls", name), "utf8");
const CA = fixture("internal-ca.pem");

test("내부 API 에이전트는 지정한 사내 CA 를 쓰고, 검증을 끄지 않는다", () => {
  const api = createInternalApi({ baseUrl: "https://pay.internal.example", caFile: "/etc/board/internal-ca.pem",
    readFile: () => CA });
  expect(api.agent.options.ca).toBe(CA);
  expect(api.agent.options.rejectUnauthorized).not.toBe(false);
});

test("CA 가 없거나 https 가 아니면 클라이언트를 만들지 않는다 (조용히 검증을 끄지 않는다)", () => {
  expect(() => createInternalAgent("")).toThrow();
  expect(() => createInternalApi({ baseUrl: "http://pay.internal.example", caFile: "x", readFile: () => CA })).toThrow();
});

test("사내 CA 가 서명한 인증서만 이어진다 (중간자의 자체 서명 인증서는 아님)", () => {
  const ca = new X509Certificate(CA);
  const leaf = new X509Certificate(fixture("internal-leaf.pem"));
  const mitm = new X509Certificate(fixture("internal-mitm.pem"));
  expect(leaf.checkIssued(ca) && leaf.verify(ca.publicKey)).toBe(true);
  expect(mitm.subject).toBe(leaf.subject); // 이름은 같아도
  expect(mitm.checkIssued(ca)).toBe(false); // 사내 CA 가 서명하지 않았다
});

test("코드에 인증서 검증 끄기가 없다", () => {
  const src = path.join(HERE, "../src");
  const files = [];
  const walk = (d) => readdirSync(d).forEach((f) => {
    const p = path.join(d, f);
    if (statSync(p).isDirectory()) walk(p); else if (p.endsWith(".js")) files.push(p);
  });
  walk(src);
  // 주석의 "✗" 예시는 빼고, 실제 코드 줄만 본다
  const code = files.map((f) => readFileSync(f, "utf8").split("\n").filter((l) => !l.trim().startsWith("//")).join("\n"));
  for (const c of code) {
    expect(c).not.toMatch(/rejectUnauthorized\s*:\s*false/);
    expect(c).not.toMatch(/NODE_TLS_REJECT_UNAUTHORIZED/);
  }
});
