// 3-6 패스키와 세션 탈취 대응
import * as simplewebauthn from "@simplewebauthn/server";
import request from "supertest";
import { PASSWORDS, login, makeApp } from "./helpers.js";

const b64url = (v) => Buffer.from(typeof v === "string" ? v : JSON.stringify(v)).toString("base64url");

// 검증 함수: 기본은 실제 @simplewebauthn/server, mode 로 결과를 바꿔 끼운다
const errors = [];
let mode = "real";
const webauthn = {
  ...simplewebauthn,
  async verifyAuthenticationResponse(opts) {
    if (mode === "verified") return { verified: true, authenticationInfo: { newCounter: 1 } };
    try {
      return await simplewebauthn.verifyAuthenticationResponse(opts);
    } catch (e) {
      errors.push(e.message);
      throw e;
    }
  },
};

let app, deps;
beforeAll(async () => {
  ({ app, deps } = await makeApp({ overrides: { webauthn } }));
  // alice 가 이미 등록해 둔 패스키 (공개키 값은 실습용 가짜)
  await deps.stores.passkeys.add({ id: "cred-alice", publicKey: new Uint8Array([1, 2, 3]), counter: 0, userId: "alice" });
});
beforeEach(() => {
  mode = "real";
  errors.length = 0;
});

// 인증기가 만든 것처럼 보이는 응답. origin 만 바꿔 피싱 사이트 상황을 만든다
const assertion = (challenge, origin) => ({
  id: "cred-alice", rawId: "cred-alice", type: "public-key", clientExtensionResults: {},
  response: {
    clientDataJSON: b64url({ type: "webauthn.get", challenge, origin }),
    authenticatorData: b64url("x".repeat(37)),
    signature: b64url("sig"),
  },
});

test("다른 오리진에서 만든 서명은 401", async () => {
  const browser = request.agent(app);
  const { challenge } = (await browser.post("/webauthn/login/options")).body;
  const res = await browser.post("/webauthn/login/finish").send(assertion(challenge, "https://board.example.net"));
  expect(res.status).toBe(401);
  expect(errors.join()).toMatch(/origin/i);                    // 오리진 검사에서 거절됐다
});

test("챌린지는 한 번만 쓸 수 있다", async () => {
  mode = "verified";                                           // 서명 검증은 통과했다고 가정
  const browser = request.agent(app);
  const { challenge } = (await browser.post("/webauthn/login/options")).body;
  const body = assertion(challenge, deps.config.webauthnOrigin);
  const first = await browser.post("/webauthn/login/finish").send(body);
  expect(first.status).toBe(204);
  expect(first.headers["set-cookie"].join()).toMatch(/sid=/);  // 새 세션 발급
  const replay = await browser.post("/webauthn/login/finish").send(body);
  expect(replay.status).toBe(401);
});

test("revoke-all 뒤 기존 토큰은 401", async () => {
  const laptop = await login(app, "alice");
  const stolen = await request(app).post("/login").send({ username: "alice", password: PASSWORDS.alice });
  const me = (t) => request(app).get("/me").set("Authorization", `Bearer ${t}`);
  expect((await me(stolen.body.token)).status).toBe(200);

  const res = await request(app).post("/me/sessions/revoke-all").set("Authorization", `Bearer ${laptop}`);
  expect(res.status).toBe(204);
  expect((await me(laptop)).status).toBe(401);
  expect((await me(stolen.body.token)).status).toBe(401);
  // 리프레시 토큰도 함께 폐기됐다
  const rt = stolen.headers["set-cookie"].find((c) => c.startsWith("rt=")).split(";")[0];
  expect((await request(app).post("/token/refresh").set("Cookie", rt)).status).toBe(401);
});
