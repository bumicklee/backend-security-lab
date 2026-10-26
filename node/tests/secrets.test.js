// 5-3 시크릿 로더: 파일 대신 Secrets Manager 에서, 용도별로 (가짜 클라이언트로 확인)
import { GetSecretValueCommand } from "@aws-sdk/client-secrets-manager";
import { loadConfig } from "../src/config.js";
import { envSource, loadSecrets, secretsManagerSource } from "../src/lib/secrets.js";

const SECRETS = {
  "prod/board-api/db": JSON.stringify({ url: "postgresql://board:from-sm-EXAMPLE@db.internal:5432/board" }),
  "prod/board-api/webhook": JSON.stringify({ secret: "whsec_from_sm_EXAMPLE" }),
};
function fakeClient(store = SECRETS) {
  const calls = [];
  return {
    calls,
    async send(cmd) {
      expect(cmd).toBeInstanceOf(GetSecretValueCommand);
      calls.push(cmd.input.SecretId);
      return { SecretString: store[cmd.input.SecretId] };
    },
  };
}

test("용도별 이름(prod/board-api/db · webhook)으로 읽어 설정에 넣는다", async () => {
  const client = fakeClient();
  const config = await loadSecrets(loadConfig({ NODE_ENV: "test" }), secretsManagerSource({ client }));
  expect(client.calls).toEqual(["prod/board-api/db", "prod/board-api/webhook"]);
  expect(config.databaseUrl).toContain("from-sm-EXAMPLE");
  expect(config.webhookSecret).toBe("whsec_from_sm_EXAMPLE");
});

test("짧게만 캐시한다: 회전한 값은 TTL 이 지나면 다시 읽는다", async () => {
  let t = 0;
  const store = { ...SECRETS };
  const client = fakeClient(store);
  const sm = secretsManagerSource({ client, ttlMs: 60_000, now: () => t });
  expect((await sm.get("prod/board-api/webhook")).secret).toBe("whsec_from_sm_EXAMPLE");
  store["prod/board-api/webhook"] = JSON.stringify({ secret: "whsec_rotated_EXAMPLE" });
  t = 30_000;
  expect((await sm.get("prod/board-api/webhook")).secret).toBe("whsec_from_sm_EXAMPLE");
  t = 61_000;
  expect((await sm.get("prod/board-api/webhook")).secret).toBe("whsec_rotated_EXAMPLE");
  expect(client.calls).toHaveLength(2);
});

test("시크릿이 없으면 시작을 멈추고, 오류 메시지에 값을 싣지 않는다", async () => {
  const client = fakeClient({ "prod/board-api/db": JSON.stringify({ url: "postgresql://board:leak-EXAMPLE@x/board" }) });
  const err = await loadSecrets(loadConfig({ NODE_ENV: "test" }), secretsManagerSource({ client })).catch((e) => e);
  expect(err).toBeInstanceOf(Error);
  expect(err.message).not.toContain("leak-EXAMPLE");
  const bad = secretsManagerSource({ client: { send: async () => ({ SecretString: "not-json-secret-EXAMPLE" }) } });
  const err2 = await bad.get("prod/board-api/db").catch((e) => e);
  expect(err2.message).not.toContain("not-json-secret-EXAMPLE");
});

test("로컬(env 소스)은 같은 모양으로 환경 변수를 돌려준다", async () => {
  const env = { DATABASE_URL: "postgresql://lab@localhost/express_board", WEBHOOK_SECRET: "whsec_env_EXAMPLE" };
  const config = await loadSecrets(loadConfig({ NODE_ENV: "test" }), envSource(env));
  expect(config.webhookSecret).toBe("whsec_env_EXAMPLE");
  expect(config.databaseUrl).toBe(env.DATABASE_URL);
});
