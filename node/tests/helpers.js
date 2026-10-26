// 테스트 공통: DB 없이(메모리 대역) 앱을 만들고, 로그인 토큰을 얻는다.
import { mkdtempSync } from "node:fs";
import { tmpdir } from "node:os";
import path from "node:path";
import request from "supertest";
import { loadConfig } from "../src/config.js";
import { createApp } from "../src/create-app.js";
import { createDeps } from "../src/data/index.js";

export async function makeApp({ labMode = false, config = {}, overrides = {} } = {}) {
  const tmp = mkdtempSync(path.join(tmpdir(), "lab-test-"));
  const cfg = {
    ...loadConfig({ NODE_ENV: "test" }),
    storeDir: path.join(tmp, "avatars"),
    labPublicDir: path.join(tmp, "lab-public"),
    ...config,
  };
  const deps = await createDeps(cfg, overrides);
  return { app: createApp(deps, { labMode }), deps, tmp };
}

export const PASSWORDS = { admin: "admin-lab-pass", alice: "alice-lab-pass", bob: "bob-lab-pass" };

export async function login(app, username = "alice") {
  const res = await request(app).post("/api/login").send({ username, password: PASSWORDS[username] });
  if (res.status !== 200) throw new Error(`login failed: ${res.status}`);
  return res.body.token;
}

// 1x1 PNG (매직 바이트가 진짜인 이미지)
export const PNG_1x1 = Buffer.from(
  "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==",
  "base64",
);
