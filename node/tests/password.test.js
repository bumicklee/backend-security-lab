// 3-1 비밀번호 저장: Argon2id + 로그인할 때 예전 해시(scrypt·bcrypt)를 이전한다
import bcrypt from "bcryptjs";
import request from "supertest";
import { hashPassword } from "../src/lib/password.js";
import { PASSWORDS, makeApp } from "./helpers.js";

let app, deps;
beforeAll(async () => {
  ({ app, deps } = await makeApp());
});

const hashOf = async (username) => (await deps.users.findOne({ username })).passwordHash;

test("같은 비밀번호도 해시가 다르다", async () => {
  const a = await hashPassword("same-password-EXAMPLE");
  const b = await hashPassword("same-password-EXAMPLE");
  expect(a).toMatch(/^\$argon2id\$v=19\$m=19456,p=1,t=2\$/); // 파라미터가 해시 안에
  expect(a).not.toBe(b);                                       // 계정마다 다른 솔트
});

test("scrypt 해시는 로그인 후 argon2id로 바뀐다", async () => {
  expect(await hashOf("alice")).toMatch(/^scrypt\$/);           // 시드는 예전 형식
  const res = await request(app).post("/login").send({ username: "alice", password: PASSWORDS.alice });
  expect(res.body.ok).toBe(true);
  expect(await hashOf("alice")).toMatch(/^\$argon2id\$v=19\$m=19456,p=1,t=2\$/);
  // 바뀐 해시로도 계속 로그인된다
  expect((await request(app).post("/login").send({ username: "alice", password: PASSWORDS.alice })).status).toBe(200);

  // 더 예전 bcrypt 해시도 같은 길로 이전된다
  await deps.users.updateOne({ username: "bob" }, { $set: { passwordHash: bcrypt.hashSync(PASSWORDS.bob, 10) } });
  expect((await request(app).post("/login").send({ username: "bob", password: PASSWORDS.bob })).status).toBe(200);
  expect(await hashOf("bob")).toMatch(/^\$argon2id\$/);
});
