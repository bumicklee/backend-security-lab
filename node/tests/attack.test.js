// 2-8 공격 재현 테스트: 수업 화면의 attack.test.js 와 같은 코드. CI 에서 PR 마다 돈다.
import request from "supertest";
import { app } from "../src/app.js";

test.each(["../../etc/passwd", "..%2f..%2fetc%2fpasswd"])(
  "경로 조작 %s 는 거절", async (name) => {
    const res = await request(app).get(`/files?name=${name}`);
    expect(res.status).toBeGreaterThanOrEqual(400);
    expect(res.text).not.toContain("root:");
  });

test("$ne 연산자 로그인은 400", async () => {
  const res = await request(app).post("/login")
    .send({ username: "admin", password: { $ne: null } });
  expect(res.status).toBe(400);
});

test.each(["' OR 1=1--", "' UNION SELECT id, email, password_hash FROM users--"])(
  "SQL 페이로드 %s 는 0건", async (title) => {
    const res = await request(app).get("/posts").query({ title });
    expect(res.status).toBe(200);
    expect(res.body).toHaveLength(0);
  });

test("메타데이터 주소 미리보기는 400", async () => {
  const res = await request(app).get("/preview")
    .query({ url: "http://169.254.169.254/latest/meta-data/" });
  expect(res.status).toBe(400);
});

test("기본 앱에는 취약 라우트(/lab)가 없다", async () => {
  expect((await request(app).get("/lab/posts")).status).toBe(404);
});
