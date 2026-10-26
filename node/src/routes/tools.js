// 관리자용 네트워크 점검 (2-4 커맨드 인젝션: exec 대신 execFile + 인자 배열)
import { Router } from "express";

const HOST = /^[a-z0-9.-]{1,253}$/i;

export function toolRoutes({ run }, auth) {
  const router = Router();

  // run = promisify(execFile) — 셸을 거치지 않는다
  router.get("/admin/ping", auth.requireAuth(), auth.requireRole("ADMIN"), async (req, res) => {
    const host = String(req.query.host);
    if (!HOST.test(host)) return res.sendStatus(400);
    try {
      const { stdout } = await run("ping", ["-c", "1", "--", host], { timeout: 5000 });
      res.type("text").send(stdout);
    } catch {
      res.status(502).type("text").send("ping failed");
    }
  });

  return router;
}
