// 4-1 Mass assignment 와 과도한 데이터 노출: PATCH /users/me
import request from "supertest";
import { PASSWORDS, login, makeApp } from "./helpers.js";

let app, token;
beforeAll(async () => {
  ({ app } = await makeApp());
  token = await login(app, "alice");
});

const patch = (url, body) => request(app).patch(url).set("Authorization", `Bearer ${token}`).send(body);
const relogin = async () =>
  (await request(app).post("/api/login").send({ username: "alice", password: PASSWORDS.alice })).body.role;

test("role을 섞은 PATCH는 400, 권한은 그대로", async () => {
  const res = await patch("/users/me", { nickname: "앨리스", role: "ADMIN" });
  expect(res.status).toBe(400);
  expect(await relogin()).toBe("USER");
});

test("응답 필드는 username·nickname·bio뿐", async () => {
  const res = await patch("/users/me", { nickname: "앨리스", bio: "안녕하세요" });
  expect(res.status).toBe(200);
  expect(Object.keys(res.body).sort()).toEqual(["bio", "nickname", "username"]);
  expect(res.body).toEqual({ username: "alice", nickname: "앨리스", bio: "안녕하세요" });
});
