// 4-5 CORS 와 CSRF: 쿠키(세션)로 인증하는 POST /account/email
import * as simplewebauthn from "@simplewebauthn/server";
import request from "supertest";
import { makeApp } from "./helpers.js";

const b64url = (v) => Buffer.from(typeof v === "string" ? v : JSON.stringify(v)).toString("base64url");
// 패스키 서명 검증은 통과했다고 가정한다 (세션 로그인을 만들기 위한 대역)
const webauthn = { ...simplewebauthn, verifyAuthenticationResponse: async () => ({ verified: true, authenticationInfo: { newCounter: 1 } }) };

let app, browser;
beforeAll(async () => {
  let deps;
  ({ app, deps } = await makeApp({ overrides: { webauthn } }));
  await deps.stores.passkeys.add({ id: "cred-alice", publicKey: new Uint8Array([1]), counter: 0, userId: "alice" });
  browser = request.agent(app);                               // 쿠키(sid)를 기억하는 "앨리스 브라우저"
  const { challenge } = (await browser.post("/webauthn/login/options")).body;
  await browser.post("/webauthn/login/finish").send({
    id: "cred-alice", rawId: "cred-alice", type: "public-key", clientExtensionResults: {},
    response: { clientDataJSON: b64url({ type: "webauthn.get", challenge, origin: deps.config.webauthnOrigin }),
      authenticatorData: b64url("x".repeat(37)), signature: b64url("sig") },
  }).expect(204);
});

test("다른 사이트에서 온 POST는 403", async () => {
  const res = await browser.post("/account/email")
    .set("Origin", "https://attacker.example.net").set("Sec-Fetch-Site", "cross-site")
    .send({ email: "attacker@example.net" });
  expect(res.status).toBe(403);
});

test("허용한 Origin의 POST는 200", async () => {
  const res = await browser.post("/account/email")
    .set("Origin", "https://app.example.com").set("Sec-Fetch-Site", "same-site")
    .send({ email: "alice2@example.com" });
  expect(res.status).toBe(200);
  expect(res.headers["access-control-allow-origin"]).toBe("https://app.example.com");
  expect(res.headers["access-control-allow-credentials"]).toBe("true");
});

test("모르는 Origin에는 Access-Control-Allow-Origin이 없다", async () => {
  const res = await request(app).get("/posts").set("Origin", "https://attacker.example.net");
  expect(res.status).toBe(200);                                // 요청은 도착한다 (CORS 는 읽기만 막는다)
  expect(res.headers["access-control-allow-origin"]).toBeUndefined();
});
