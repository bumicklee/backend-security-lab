// 첨부 파일 내려받기 (2-4 경로 조작: sendFile 의 root 옵션)
import { Router } from "express";

export function fileRoutes({ config }) {
  const router = Router();

  router.get("/files", (req, res, next) => {
    const name = String(req.query.name);
    // root 밖으로 나가는 경로는 403
    res.sendFile(name, {
      root: config.filesDir,
      dotfiles: "deny",
    }, (err) => {
      if (err) next(err.status ? err : Object.assign(err, { status: 404 }));
    });
  });

  return router;
}
