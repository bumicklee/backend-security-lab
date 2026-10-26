// ⚠️ 의도적으로 취약한 실습 코드 — 운영에 쓰지 마세요
// LAB_MODE=vulnerable + HOST=127.0.0.1 일 때만 /lab 아래에 등록된다.
// 2-4 경로 조작: path.join 이 ../ 를 계산해 버린다.
import path from "node:path";
import { Router } from "express";

export function labFileRoutes({ config }) {
  const router = Router();

  router.get("/files", (req, res) => {
    const p = path.join(config.filesDir, String(req.query.name));
    res.sendFile(p, { dotfiles: "allow" }, (err) => {
      if (err) res.sendStatus(err.status ?? 404);
    });
  });

  return router;
}
