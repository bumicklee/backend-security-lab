// ⚠️ 의도적으로 취약한 실습 코드 — 운영에 쓰지 마세요
// LAB_MODE=vulnerable + HOST=127.0.0.1 일 때만 /lab 아래에 등록된다.
// 2-4 커맨드 인젝션: 셸이 문자열 전체를 해석한다.
import { exec } from "node:child_process";
import { Router } from "express";

export function labToolRoutes() {
  const router = Router();

  router.get("/admin/ping", (req, res) => {
    exec(`ping -c 1 ${req.query.host}`, { timeout: 20_000 }, (err, stdout) => {
      res.type("text").send(stdout);
    });
  });

  return router;
}
