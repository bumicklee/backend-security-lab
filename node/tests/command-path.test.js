// 2-4 커맨드 인젝션과 경로 조작: execFile + 인자 배열, sendFile root 옵션
import request from "supertest";
import { login, makeApp } from "./helpers.js";

let app, adminToken;
const calls = [];
beforeAll(async () => {
  // 실제 ping 대신 가짜 실행기를 주입한다 (어떤 인자로 불렸는지만 기록)
  const run = async (file, args) => {
    calls.push([file, args]);
    return { stdout: `PING ${args.at(-1)}: 1 packets transmitted, 1 received\n` };
  };
  ({ app } = await makeApp({ labMode: true, overrides: { run } }));
  adminToken = await login(app, "admin");
});

const ping = (host) =>
  request(app).get("/admin/ping").query({ host }).set("Authorization", `Bearer ${adminToken}`);

test("sleep 5 주입은 1초 안에 400", async () => {
  const start = Date.now();
  const res = await ping("203.0.113.10; sleep 5");
  expect(res.status).toBe(400);
  expect(Date.now() - start).toBeLessThan(1000);
  expect(calls).toHaveLength(0); // 명령이 아예 실행되지 않았다
});

test.each(["203.0.113.10 && id", "$(id)", "`id`", "a b", "a|b"])("셸 문자·옵션 %s 는 400", async (host) => {
  expect((await ping(host)).status).toBe(400);
});

test("정상 호스트는 셸 없이 인자 배열로 실행된다", async () => {
  const res = await ping("203.0.113.10");
  expect(res.status).toBe(200);
  expect(calls.at(-1)).toEqual(["ping", ["-c", "1", "--", "203.0.113.10"]]);
});

test("옵션처럼 생긴 값은 -- 뒤에 와서 인자로만 전달된다", async () => {
  await ping("-f");
  expect(calls.at(-1)).toEqual(["ping", ["-c", "1", "--", "-f"]]);
});

test("일반 사용자는 관리자 점검 API 를 쓸 수 없다", async () => {
  const token = await login(app, "alice");
  const res = await request(app).get("/admin/ping").query({ host: "203.0.113.10" }).set("Authorization", `Bearer ${token}`);
  expect(res.status).toBe(403);
});

test("허용된 폴더의 파일은 내려받는다", async () => {
  const res = await request(app).get("/files").query({ name: "welcome.txt" });
  expect(res.status).toBe(200);
});

test.each(["../../package.json", "..%2f..%2fpackage.json", "../../../../../../etc/passwd"])(
  "인코딩한 ../ 는 root 밖으로 못 나간다: %s",
  async (name) => {
    const res = await request(app).get(`/files?name=${name}`);
    expect(res.status).toBeGreaterThanOrEqual(400);
    expect(res.text).not.toContain("backend-security-lab-express");
    expect(res.text).not.toContain("root:");
  },
);

describe("수정 전 코드(/lab)는 뚫린다", () => {
  test("세미콜론 뒤 명령이 실행된다", async () => {
    // 실제 셸이 돈다. 무해한 echo 만 사용
    const res = await request(app).get("/lab/admin/ping").query({ host: ";echo INJECTED-BY-TEST" });
    expect(res.text).toContain("INJECTED-BY-TEST");
  });
  test("../ 로 폴더 밖 파일(package.json)을 읽는다", async () => {
    const res = await request(app).get("/lab/files?name=..%2f..%2fpackage.json");
    expect(res.status).toBe(200);
    expect(res.text).toContain("backend-security-lab-express");
  });
});
