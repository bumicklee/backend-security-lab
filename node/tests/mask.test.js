// 0-3 로그에 개인정보를 남기지 않는다: 마스킹 + 접속 기록에 본문·쿼리 제외
import request from "supertest";
import { Mask } from "../src/lib/mask.js";
import { makeApp } from "./helpers.js";

test("전화번호는 가운데를 가린다", () => {
  expect(Mask.phone("010-1234-5678")).toBe("010-****-5678");
  expect(Mask.phone("01012345678")).toBe("010-****-5678");
});

test("카드번호는 끝 4자리만", () => {
  expect(Mask.last4("4111-1111-1111-1111")).toBe("****-****-****-1111");
});

test("이메일은 앞 글자와 도메인만", () => {
  expect(Mask.email("alice@example.com")).toBe("a***@example.com");
});

test("접속 기록에는 비밀번호·쿼리스트링이 남지 않는다", async () => {
  const lines = [];
  const logger = { debug() {}, info: (msg, f) => lines.push(JSON.stringify({ msg, ...f })), warn() {}, error() {} };
  const { app } = await makeApp({ overrides: { logger } });
  await request(app).post("/login").send({ username: "alice", password: "alice-lab-pass" });
  await request(app).get("/posts").query({ title: "010-1234-5678" });
  const all = lines.join("\n");
  expect(all).toContain('"path":"/login"');
  expect(all).not.toContain("alice-lab-pass");
  expect(all).not.toContain("010-1234-5678");
});
