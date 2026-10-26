// 5-5 애플리케이션 안에서도 반출을 줄인다: 필요한 컬럼만, 1,000건씩, 누적 건수 감시
import request from "supertest";
import { login, makeApp } from "./helpers.js";

// 시드 회원(3명, 그중 1명 탈퇴) 뒤에 n 명을 한 번에 넣는다
async function addMembers(deps, n) {
  const values = Array.from({ length: n }, (_, i) => `('m${i}', 'm${i}@example.com')`).join(",");
  await deps.prisma.$queryRawUnsafe(`INSERT INTO members (nickname, email) VALUES ${values}`);
}

async function setup(config = {}) {
  const alerts = [];
  const { app, deps } = await makeApp({ config, overrides: { alert: (event, f) => alerts.push({ event, ...f }) } });
  await addMembers(deps, 1500);
  const token = await login(app, "admin");
  const get = (q) => request(app).get("/admin/members/export").query(q).set("Authorization", `Bearer ${token}`);
  return { app, deps, alerts, get };
}

test("한 번에 1,000건, id 오름차순으로 나눠 내보낸다", async () => {
  const { get } = await setup();
  const p0 = await get({ page: 0 });
  expect(p0.status).toBe(200);
  expect(p0.body).toHaveLength(1000);
  const ids = p0.body.map((r) => r.id);
  expect(ids).toEqual([...ids].sort((a, b) => a - b));
  const p1 = await get({ page: 1 });
  expect(p1.body).toHaveLength(502);           // 활성 회원 1,502명 (탈퇴 1명 제외)
  expect(p1.body[0].id).toBeGreaterThan(ids.at(-1));
  expect((await get({ page: 2 })).body).toEqual([]);
});

test("기본은 id·닉네임만, 허용 목록 밖 컬럼(연락처 암호문·인덱스)은 400", async () => {
  const { get } = await setup();
  const res = await get({ page: 0 });
  expect(Object.keys(res.body[0]).sort()).toEqual(["id", "nickname"]);
  const withEmail = await get({ page: 0, fields: "nickname,email" });
  expect(Object.keys(withEmail.body[0]).sort()).toEqual(["email", "id", "nickname"]);
  for (const fields of ["phoneCt", "phoneIdx", "nickname,passwordHash", ""]) {
    expect((await get({ page: 0, fields })).status).toBe(400);
  }
});

test("오늘 누적 건수가 기준을 넘으면 알리고, 접속기록에 처리 건수를 남긴다", async () => {
  const { get, alerts, deps } = await setup({ exportAlertThreshold: 1200 });
  await get({ page: 0 });                       // 누적 1,000
  expect(alerts).toEqual([]);
  await get({ page: 1 });                       // 누적 1,502 > 1,200
  expect(alerts).toEqual([{ event: "bulk-export", actorId: "admin", total: 1502 }]);
  const log = deps.auditLog.entries();
  expect(log.map((e) => [e.task, e.count])).toEqual([["회원 다운로드", 1000], ["회원 다운로드", 502]]);
});

test("관리자가 아니면 403", async () => {
  const { app } = await makeApp();
  const token = await login(app, "alice");
  expect((await request(app).get("/admin/members/export").set("Authorization", `Bearer ${token}`)).status).toBe(403);
});
