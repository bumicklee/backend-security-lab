// 4-4 Rate limiting: trust proxy 는 실제로 거치는 프록시(1홉)만 신뢰한다
import request from "supertest";
import { makeApp as make } from "./helpers.js";

// 로드밸런서 한 단계 뒤에 배포한 상황: TRUST_PROXY=1
const makeApp = ({ config = {} } = {}) => make({ config: { trustProxy: 1, ...config } });

// 로드밸런서는 클라이언트가 보낸 X-Forwarded-For 뒤에 실제 접속 IP 를 덧붙인다.
// trust proxy = 1 이면 맨 뒤(LB 가 붙인) 값이 req.ip 가 된다.
const viaLb = (app, i) =>
  request(app).get("/posts").set("X-Forwarded-For", `198.51.100.${i}, 203.0.113.66`);

test("X-Forwarded-For를 바꿔도 101번째 요청은 429", async () => {
  const { app } = await makeApp();
  for (let i = 1; i <= 100; i++) expect((await viaLb(app, i)).status).toBe(200);
  const res = await viaLb(app, 101);
  expect(res.status).toBe(429);
});

test("제한에 걸리면 Retry-After 와 RateLimit 헤더를 준다", async () => {
  const { app } = await makeApp({ config: { rateLimitPerMin: 2 } });
  await viaLb(app, 1);
  await viaLb(app, 2);
  const res = await viaLb(app, 3);
  expect(res.status).toBe(429);
  expect(Number(res.headers["retry-after"])).toBeGreaterThan(0);
  expect(res.headers["ratelimit-policy"]).toMatch(/q=2; w=60/);
  expect(res.headers["ratelimit"]).toMatch(/r=0/);
});
