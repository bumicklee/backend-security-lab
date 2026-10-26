// 5-4 개인정보보호법 관점의 개발: 최소 수집 · 접속기록 다섯 항목 · 보존 기간 파기 배치
import request from "supertest";
import { purgeExpired } from "../src/jobs/purge-expired.js";
import { login, makeApp } from "./helpers.js";

describe("최소 수집 (POST /signup)", () => {
  const ok = { email: "dave@example.com", password: "dave-lab-pass-1234", nickname: "dave", over14: true };

  test("필요한 것(이메일·비밀번호·닉네임·14세 이상)만 받는다", async () => {
    const { app, deps } = await makeApp();
    const res = await request(app).post("/signup").send(ok);
    expect(res.status).toBe(201);
    expect(res.body).toEqual({ username: "dave@example.com", nickname: "dave" });
    const saved = await deps.users.findOne({ username: "dave@example.com" });
    expect(saved.passwordHash).toMatch(/^\$argon2id\$/);
    expect(await request(app).post("/login").send({ username: "dave@example.com", password: ok.password }))
      .toHaveProperty("status", 200);
  });

  test("주민등록번호·주소처럼 받지 않기로 한 필드가 오면 400", async () => {
    const { app } = await makeApp();
    for (const extra of [{ residentNo: "900101-1234567" }, { address: "서울시 어딘가" }, { phone: "010-1234-5678" }]) {
      const res = await request(app).post("/signup").send({ ...ok, ...extra });
      expect(res.status).toBe(400);
      expect(JSON.stringify(res.body)).not.toContain("900101"); // 오류 응답에 값을 되돌려 주지 않는다
    }
  });

  test("14세 미만·짧은 비밀번호는 400", async () => {
    const { app } = await makeApp();
    expect((await request(app).post("/signup").send({ ...ok, over14: false })).status).toBe(400);
    expect((await request(app).post("/signup").send({ ...ok, password: "short" })).status).toBe(400);
  });
});

describe("접속기록 (lib/privacy-access.js)", () => {
  test("관리자가 회원을 볼 때 식별자·접속일시·접속지·처리한 정보주체·수행업무를 남긴다", async () => {
    const { app, deps } = await makeApp();
    const token = await login(app, "admin");
    const res = await request(app).get("/admin/members/1").set("Authorization", `Bearer ${token}`);
    expect(res.status).toBe(200);
    expect(res.body).toEqual({ id: 1, nickname: "alice", email: "a***@example.com", phone: "010-****-6789" });
    const [e] = deps.auditLog.entries();
    expect(e).toEqual({
      actorId: "admin", at: expect.stringMatching(/^\d{4}-\d\d-\d\dT/), from: expect.any(String),
      subjects: [1], task: "회원 조회", status: 200, count: 1 });
  });

  test("전화번호 검색은 블라인드 인덱스로 찾고, 찾은 회원 수만큼 기록한다", async () => {
    const { app, deps } = await makeApp();
    const token = await login(app, "admin");
    const res = await request(app).get("/admin/members").query({ phone: "01034567890" })
      .set("Authorization", `Bearer ${token}`);
    expect(res.body).toEqual([{ id: 2, nickname: "bob", email: "b***@example.com", phone: "010-****-7890" }]);
    expect(deps.auditLog.entries()[0]).toMatchObject({ task: "회원 검색", subjects: [2], count: 1 });
    // DB 에는 평문 번호가 없다
    const [row] = await deps.prisma.member.findMany({ where: { id: 2 } });
    expect(JSON.stringify(row)).not.toContain("3456");
  });

  test("권한 없는 조회 시도도 남긴다 (403, 처리한 정보주체 없음)", async () => {
    const { app, deps } = await makeApp();
    const token = await login(app, "bob");
    expect((await request(app).get("/admin/members/1").set("Authorization", `Bearer ${token}`)).status).toBe(403);
    expect(deps.auditLog.entries()).toEqual([expect.objectContaining({ actorId: "bob", status: 403, subjects: [], count: 0 })]);
  });

  test("접속기록 저장소는 쓰기만 된다 (고치기·지우기 메서드 없음)", async () => {
    const { deps } = await makeApp();
    await deps.auditLog.write({ actorId: "admin", task: "회원 조회" });
    expect(deps.auditLog.update).toBeUndefined();
    expect(deps.auditLog.delete).toBeUndefined();
    const [e] = deps.auditLog.entries();
    expect(Object.isFrozen(e)).toBe(true);
  });
});

describe("파기 배치 (purgeExpired)", () => {
  test("탈퇴 후 보존 기간이 지난 회원은 지우고, 오래된 주문은 구매자를 익명화한다", async () => {
    const { deps } = await makeApp();
    // 6년 전 주문 하나를 넣는다 (주문 보존 기간 5년)
    await deps.prisma.$queryRawUnsafe(
      "INSERT INTO orders (user_id, product_id, qty, total, created_at) VALUES ('alice', 7, 1, 39000, now() - interval '2200 days')");
    const before = await deps.prisma.member.count();
    const r = await purgeExpired(deps);
    expect(r).toEqual({ members: 1, orders: 1 });   // carol(400일 전 탈퇴) · 6년 전 주문
    expect(await deps.prisma.member.count()).toBe(before - 1);
    const anonymized = await deps.prisma.order.findMany({ where: { userId: "anonymized" } });
    expect(anonymized).toHaveLength(1);
    // 최근 주문(시드 1001: alice)은 그대로
    expect(await deps.prisma.order.findFirst({ where: { id: 1001 } })).toMatchObject({ userId: "alice" });
  });
});
