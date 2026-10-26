// 5-3 시크릿 로더: 서버의 .env 파일 대신 시크릿 매니저에서, 용도별로 읽는다.
// 이름 규칙: 환경/서비스/용도 → prod/board-api/db, prod/board-api/webhook
// 소스는 { get(secretId) } 하나만 맞추면 되므로 테스트에서는 가짜 클라이언트를 넣는다.
import { GetSecretValueCommand, SecretsManagerClient } from "@aws-sdk/client-secrets-manager";

// 키 파일 없음: 서버 역할(인스턴스 프로필·IRSA)의 권한으로 읽는다.
// 회전이 반영되도록 짧게만(기본 5분) 캐시한다.
export function secretsManagerSource({ client = new SecretsManagerClient({}), ttlMs = 5 * 60_000, now = Date.now } = {}) {
  const cache = new Map(); // secretId → { value, until }
  return {
    async get(secretId) {
      const hit = cache.get(secretId);
      if (hit && hit.until > now()) return hit.value;
      const { SecretString } = await client.send(
        new GetSecretValueCommand({ SecretId: secretId }));
      if (!SecretString) throw new Error(`시크릿이 비어 있습니다: ${secretId}`);
      const value = parseSecret(SecretString);
      cache.set(secretId, { value, until: now() + ttlMs });
      return value;
    },
  };
}

// 로컬·compose: 이미 환경 변수로 들어온 값을 같은 모양으로 돌려준다 (시크릿 매니저가 없을 때)
const ENV_NAMES = { db: { url: "DATABASE_URL" }, webhook: { secret: "WEBHOOK_SECRET" } };
export function envSource(env = process.env) {
  return {
    async get(secretId) {
      const purpose = secretId.split("/").pop();
      const names = ENV_NAMES[purpose];
      if (!names) throw new Error(`알 수 없는 시크릿 용도: ${purpose}`);
      return Object.fromEntries(Object.entries(names).map(([k, name]) => [k, env[name]]));
    },
  };
}

// 시작할 때 한 번: 용도별 시크릿을 읽어 설정에 넣는다. 값은 로그에 남기지 않는다.
export async function loadSecrets(config, source, prefix = config.secretsPrefix) {
  const db = await source.get(`${prefix}/db`);
  const webhook = await source.get(`${prefix}/webhook`);
  if (!db?.url || !webhook?.secret) {
    // 어떤 시크릿이 빠졌는지만 알리고, 값은 메시지에 넣지 않는다
    throw new Error(`필수 시크릿이 없습니다: ${prefix}/db.url, ${prefix}/webhook.secret 확인`);
  }
  return { ...config, databaseUrl: db.url, webhookSecret: webhook.secret };
}

function parseSecret(s) {
  try {
    return JSON.parse(s);
  } catch {
    throw new Error("시크릿 값은 JSON 이어야 합니다"); // 값 자체는 메시지에 넣지 않는다
  }
}
