// ⚠️ 의도적으로 취약한 실습 코드 — 운영에 쓰지 마세요
// LAB_MODE=vulnerable + HOST=127.0.0.1 일 때만 /lab 아래에 등록된다.
import { Router } from "express";
import { labAvatarRoutes } from "./avatars.js";
import { labFileRoutes } from "./files.js";
import { labLoginRoutes } from "./login.js";
import { labOrderRoutes } from "./orders.js";
import { labPostViewRoutes } from "./post-view.js";
import { labPostRoutes } from "./posts.js";
import { labPreviewRoutes } from "./preview.js";
import { labProfileRoutes } from "./profile.js";
import { labSettingsRoutes } from "./settings.js";
import { labToolRoutes } from "./tools.js";
import { labUserRoutes } from "./users.js";
import { labWebhookRoutes } from "./webhooks.js";

export function createLabRouter(deps, auth) {
  const router = Router();
  router.use((req, res, next) => {
    res.set("X-Lab-Mode", "vulnerable");
    next();
  });
  for (const make of [
    labPostRoutes, labLoginRoutes, labOrderRoutes, labUserRoutes, labToolRoutes,
    labFileRoutes, labAvatarRoutes, labPreviewRoutes, labSettingsRoutes, labWebhookRoutes,
    labPostViewRoutes, labProfileRoutes,
  ]) {
    router.use(make(deps, auth));
  }
  // Express 기본 오류 화면처럼 오류 내용을 그대로 드러낸다 (이것도 나쁜 예)
  router.use((err, req, res, next) => {
    res.status(500).type("text").send(`${err.name}: ${err.message}`);
  });
  return router;
}
