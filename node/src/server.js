// 서버 진입점. LAB_MODE=vulnerable 은 127.0.0.1 바인딩일 때만 허용한다.
import { assertSafeBind, loadConfig } from "./config.js";
import { createApp } from "./create-app.js";
import { createDeps } from "./data/index.js";
import { purgeExpired, scheduleDaily } from "./jobs/purge-expired.js";
import { loadSecrets, secretsManagerSource } from "./lib/secrets.js";

let config = loadConfig();
try {
  assertSafeBind(config);
} catch (e) {
  console.error(`[시작 거부] ${e.message}`);
  process.exit(1);
}

// 5-3 SECRETS_SOURCE=aws: DB 주소·웹훅 시크릿을 Secrets Manager 에서 (서버 역할 권한, 키 파일 없음)
if (config.secretsSource === "aws") {
  config = await loadSecrets(config, secretsManagerSource());
}

const deps = await createDeps(config);
const app = createApp(deps, { labMode: config.labMode });

const server = app.listen(config.port, config.host, () => {
  deps.logger.info("listening", {
    url: `http://${config.host}:${config.port}`,
    labMode: config.labMode ? "vulnerable" : "off",
    dataBackend: config.dataBackend,
  });
});

// 5-4 매일 03:30 파기 배치
const stopPurge = scheduleDaily("03:30", () =>
  purgeExpired(deps).catch((e) => deps.logger.error("purge failed", { err: e.message })));

const shutdown = () => {
  stopPurge();
  server.close(async () => {
    await deps.close();
    process.exit(0);
  });
};
process.on("SIGINT", shutdown);
process.on("SIGTERM", shutdown);
