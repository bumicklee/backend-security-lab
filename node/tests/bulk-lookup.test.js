// 6-2 계정별 조회량 감시: 300번째 알림 한 번, 1000번 초과 429, 1001번째 차단 이벤트 한 번
import { mkdtempSync, readFileSync } from "node:fs";
import { tmpdir } from "node:os";
import path from "node:path";
import request from "supertest";
import { createSecurityEvents } from "../src/lib/security-events.js";
import { createMemoryCounter } from "../src/lib/lookup-counter.js";
import { bulkLookupGuard } from "../src/middleware/bulk-lookup.js";
import { login, makeApp } from "./helpers.js";

const ISO = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z$/;
const today = () => new Date().toISOString().slice(0, 10);

// 미들웨어만 1,001번 부른다 (네트워크 없이 빠르게)
async function hit(guard, user = "u-90001", ip = "198.51.100.20") {
  let status = 200;
  const res = { status(s) { status = s; return this; }, json() { return this; } };
  let passed = false;
  await guard({ user: { sub: user }, ip }, res, () => { passed = true; });
  return passed ? 200 : status;
}

test("천한 번 조회하면 천 번은 통과, 마지막 한 번은 429", async () => {
  const events = createSecurityEvents();
  const guard = bulkLookupGuard(createMemoryCounter(), events);
  const counts = {};
  for (let i = 1; i <= 1001; i++) {
    const s = await hit(guard);
    counts[s] = (counts[s] ?? 0) + 1;
  }
  expect(counts).toEqual({ 200: 1000, 429: 1 });
  const ev = events.entries();
  expect(ev.map((e) => [e.event, e.user, e.count, e.ip])).toEqual([
    ["bulk_lookup", "u-90001", 300, "198.51.100.20"],
    ["bulk_block", "u-90001", 1001, "198.51.100.20"],
  ]);
  for (const e of ev) expect(e.ts).toMatch(ISO);
});

test("알림·차단 이벤트는 한 번씩만, 다른 계정은 따로 센다", async () => {
  const events = createSecurityEvents();
  const counter = createMemoryCounter();
  const guard = bulkLookupGuard(counter, events);
  await counter.set(`lookup:u-1:${today()}`, 1001);
  for (let i = 0; i < 5; i++) expect(await hit(guard, "u-1")).toBe(429);
  expect(await hit(guard, "u-2")).toBe(200);
  expect(events.entries()).toHaveLength(0);   // 1002번째부터는 이벤트를 또 남기지 않는다
});

test("키에는 하루 만료(2일)가 걸린다", async () => {
  let t = Date.parse("2026-10-20T00:00:00Z");
  const counter = createMemoryCounter(() => t);
  expect(await counter.incr("k")).toBe(1);
  await counter.expire("k", 2 * 86400);
  t += 2 * 86400_000 + 1;
  expect(await counter.incr("k")).toBe(1);
});

test("security.log 는 줄마다 JSON, ts·event·user·count·ip (fail2ban·jq 가 읽는 모양)", async () => {
  const file = path.join(mkdtempSync(path.join(tmpdir(), "seclog-")), "security.log");
  const events = createSecurityEvents({ file, now: () => new Date("2026-10-20T05:11:03.120Z") });
  events.emit("bulk_block", { user: "u-90001", ip: "198.51.100.20", count: 1001 });
  events.emit("authz_denied", { user: "bob", ip: "203.0.113.77", path: "/admin/ping" });
  await new Promise((r) => setTimeout(r, 50));
  const lines = readFileSync(file, "utf8").trim().split("\n");
  expect(JSON.parse(lines[0])).toEqual({ ts: "2026-10-20T05:11:03Z", event: "bulk_block", user: "u-90001", ip: "198.51.100.20", count: 1001 });
  // ops/fail2ban/filter.d/board-security.conf 의 failregex 와 같은 식 (<HOST> → IP)
  const failregex = /"event":"(authz_denied|validation_reject)".*"ip":"([0-9.]+)"/;
  expect(failregex.exec(lines[0])).toBeNull();
  expect(failregex.exec(lines[1])[2]).toBe("203.0.113.77");
});

test("GET /api/addresses: 로그인 필요, 1000번을 넘은 계정은 429 review_required", async () => {
  const { app, deps } = await makeApp();
  expect((await request(app).get("/api/addresses")).status).toBe(401);
  const token = await login(app, "alice");
  const get = () => request(app).get("/api/addresses").set("Authorization", `Bearer ${token}`);
  const ok = await get();
  expect(ok.status).toBe(200);
  expect(ok.body[0].label).toBe("집");
  await deps.lookupCounter.set(`lookup:alice:${today()}`, 1000);   // 오늘 이미 1,000번 조회한 상태
  const blocked = await get();
  expect(blocked.status).toBe(429);
  expect(blocked.body).toEqual({ error: "review_required" });
  expect(deps.securityEvents.entries().map((e) => e.event)).toEqual(["bulk_block"]);
  expect((await request(app).get("/addresses").set("Authorization", `Bearer ${token}`)).status).toBe(429); // 짧은 경로도 같은 카운터
});

test("관리자 경로 403 은 authz_denied 보안 이벤트로 남는다", async () => {
  const { app, deps } = await makeApp();
  const token = await login(app, "bob");
  const res = await request(app).get("/api/admin/members?phone=010-1234-5678").set("Authorization", `Bearer ${token}`);
  expect(res.status).toBe(403);
  const ev = deps.securityEvents.entries().find((e) => e.event === "authz_denied");
  expect(ev).toMatchObject({ user: "bob", path: "/admin/members" });
  expect(ev.ip).toBeTruthy();
});
