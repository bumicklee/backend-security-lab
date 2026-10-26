// 5-7 서명키를 꺼내지 않고 KMS 에 서명을 요청한다 + 발급 기록에 없는 토큰은 거부한다
import jwt from "jsonwebtoken";
import request from "supertest";
import { createIssuedStore } from "../src/data/stores.js";
import { createKmsJwtSigner, kmsPublicKeyPem } from "../src/lib/kms-jwt.js";
import { createLocalKms } from "../src/lib/local-kms.js";
import { makeApp } from "./helpers.js";

const SIGNING_KEY = "alias/board-jwt-signing";

test("KMS Sign(RSASSA_PKCS1_V1_5_SHA_256)으로 만든 토큰은 표준 RS256 으로 검증되고, 발급 기록이 남는다", async () => {
  const kms = createLocalKms();
  const issued = createIssuedStore();
  const signToken = createKmsJwtSigner({ kms, keyId: SIGNING_KEY, issued });
  const now = Math.floor(Date.now() / 1000);
  const token = await signToken({ sub: "alice", jti: "jti-1", iat: now, exp: now + 900 });

  const sign = kms.calls.find((c) => c.name === "SignCommand");
  expect(sign.input).toMatchObject({
    KeyId: SIGNING_KEY, MessageType: "RAW", SigningAlgorithm: "RSASSA_PKCS1_V1_5_SHA_256" });
  expect(Buffer.from(sign.input.Message).toString()).toBe(token.split(".").slice(0, 2).join("."));

  const pub = await kmsPublicKeyPem(kms, SIGNING_KEY);
  expect(jwt.verify(token, pub, { algorithms: ["RS256"] })).toMatchObject({ sub: "alice", jti: "jti-1" });
  expect(jwt.decode(token, { complete: true }).header).toEqual({ alg: "RS256", typ: "JWT", kid: SIGNING_KEY });
  expect(await issued.has("jti-1")).toBe(true);
});

describe("앱: JWT_KMS_KEY_ID 를 켜면 로그인 토큰을 KMS 로 서명한다", () => {
  async function setup() {
    const kms = createLocalKms();
    const { app, deps } = await makeApp({ config: { jwtKmsKeyId: SIGNING_KEY }, overrides: { kms } });
    return { app, deps, kms };
  }

  test("로그인 → KMS 서명 토큰으로 /me 200", async () => {
    const { app, kms } = await setup();
    const res = await request(app).post("/login").send({ username: "alice", password: "alice-lab-pass" });
    expect(res.status).toBe(200);
    expect(kms.calls.some((c) => c.name === "SignCommand")).toBe(true);
    const me = await request(app).get("/me").set("Authorization", `Bearer ${res.body.token}`);
    expect(me.body).toEqual({ user: "alice", role: "USER" });
  });

  test("서명키가 새어 서명이 맞는 위조 토큰이어도, 발급 기록에 없으면 401", async () => {
    const { app, deps, kms } = await setup();
    const forged = jwt.sign({ sub: "admin", role: "ADMIN" }, kms.leakSigningKeyForTest(), {
      algorithm: "RS256", expiresIn: 900, jwtid: "never-issued", issuer: deps.config.jwtIssuer });
    expect(jwt.verify(forged, deps.keys.publicKey, { algorithms: ["RS256"] })).toBeTruthy(); // 서명은 맞다
    const res = await request(app).get("/admin/users").set("Authorization", `Bearer ${forged}`);
    expect(res.status).toBe(401);
  });

  test("JWT_KMS_KEY_ID 만 있고 KMS_KEY_ID 가 없으면 시작하지 않는다", async () => {
    await expect(makeApp({ config: { jwtKmsKeyId: SIGNING_KEY } })).rejects.toThrow("KMS_KEY_ID");
  });
});
