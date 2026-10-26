// 3-3 OAuth 2.0 · OIDC: PKCE(S256) · state · nonce 를 세션에 두고 한 번만 쓴다.
// 실제 IdP 대신, 엔드포인트를 직접 적은 설정 + customFetch 로 만든 가짜 IdP(토큰·JWKS 응답)를 쓴다.
import { generateKeyPairSync } from "node:crypto";
import jwt from "jsonwebtoken";
import * as client from "openid-client";
import request from "supertest";
import { makeApp } from "./helpers.js";

const ISSUER = "https://accounts.example.com";
const { privateKey, publicKey } = generateKeyPairSync("rsa", { modulusLength: 2048 });
const issued = new Map(); // code → nonce (가짜 IdP 가 기억하는 인가 코드)

// 가짜 IdP: 토큰 엔드포인트와 JWKS 만 흉내 낸다
async function fakeIdp(url, init) {
  const u = new URL(url);
  if (u.pathname === "/jwks") {
    return Response.json({ keys: [{ ...publicKey.export({ format: "jwk" }), kid: "k1", alg: "RS256", use: "sig" }] });
  }
  if (u.pathname === "/oauth2/token") {
    const form = new URLSearchParams(String(init.body));
    const nonce = issued.get(form.get("code"));
    issued.delete(form.get("code"));                       // 코드는 한 번만
    if (!nonce || !form.get("code_verifier")) return Response.json({ error: "invalid_grant" }, { status: 400 });
    const idToken = jwt.sign({ sub: "idp-user-42", nonce }, privateKey, {
      algorithm: "RS256", keyid: "k1", issuer: ISSUER, audience: "board-web", expiresIn: 300,
    });
    return Response.json({ access_token: "at-EXAMPLE", token_type: "Bearer", expires_in: 300, id_token: idToken });
  }
  return new Response("not found", { status: 404 });
}

async function oidcDiscovery() {
  const config = new client.Configuration(
    { issuer: ISSUER, authorization_endpoint: `${ISSUER}/authorize`,
      token_endpoint: `${ISSUER}/oauth2/token`, jwks_uri: `${ISSUER}/jwks` },
    "board-web", "EXAMPLE-only-secret");
  config[client.customFetch] = fakeIdp;
  return config;
}

let app;
beforeAll(async () => {
  ({ app } = await makeApp({ overrides: { oidcDiscovery } }));
});

test("로그인 시작 시 code_challenge_method=S256", async () => {
  const res = await request(app).get("/login/idp");
  expect(res.status).toBe(302);
  const q = new URL(res.headers.location).searchParams;
  expect(res.headers.location).toMatch(/^https:\/\/accounts\.example\.com\/authorize\?/);
  expect(q.get("code_challenge_method")).toBe("S256");
  expect(q.get("code_challenge")).toMatch(/^[A-Za-z0-9_-]{43}$/);
  expect(q.get("state")).toBeTruthy();
  expect(q.get("nonce")).toBeTruthy();
  expect(q.get("redirect_uri")).toBe("https://board.example.com/callback"); // 등록한 값과 글자 그대로
  expect(res.headers["set-cookie"].join()).toMatch(/sid=.*HttpOnly/);         // state 등은 이 세션에만
});

test("세션에 저장된 값이 없는 콜백은 400", async () => {
  const res = await request(app).get("/callback").query({ code: "stolen-code", state: "x" });
  expect(res.status).toBe(400);
});

test("콜백 값은 한 번만 쓸 수 있다", async () => {
  const browser = request.agent(app);
  const start = await browser.get("/login/idp");
  const q = new URL(start.headers.location).searchParams;
  issued.set("code-1", q.get("nonce"));                     // 사용자가 IdP 에서 로그인했다

  const callback = `/callback?code=code-1&state=${q.get("state")}`;
  const ok = await browser.get(callback);
  expect(ok.status).toBe(302);
  expect(ok.headers.location).toBe("/");

  const replay = await browser.get(callback);                // 같은 콜백을 다시 보낸다
  expect(replay.status).toBe(400);
});
