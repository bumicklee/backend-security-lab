// 프로필 이미지 업로드 (2-5 파일 업로드: 매직 바이트 판별, 새 이름, 웹 루트 밖 저장)
import { randomUUID } from "node:crypto";
import { mkdir, writeFile } from "node:fs/promises";
import path from "node:path";
import { Router } from "express";
import { fileTypeFromBuffer } from "file-type";
import multer from "multer";

const ID = /^[0-9a-f-]{36}\.(png|jpg)$/;

export function avatarRoutes({ config }, auth) {
  const router = Router();
  const STORE_DIR = config.storeDir; // 웹 루트 밖. express.static 으로 서빙하지 않는다

  const upload = multer({
    // 디스크에 쓰기 전에 내용을 검사한다
    storage: multer.memoryStorage(),
    limits: { fileSize: 2e6, files: 1 },
  });

  router.post("/avatars", auth.requireAuth(), upload.single("file"), async (req, res) => {
    if (!req.file) return res.sendStatus(400);
    const t = await fileTypeFromBuffer(req.file.buffer); // 매직 바이트로 판별
    if (!["png", "jpg"].includes(t?.ext)) return res.sendStatus(400);
    const id = `${randomUUID()}.${t.ext}`; // 원본 이름은 버린다
    await mkdir(STORE_DIR, { recursive: true });
    await writeFile(path.join(STORE_DIR, id), req.file.buffer);
    res.status(201).json({ id });
  });

  router.get("/avatars/:id", (req, res, next) => {
    if (!ID.test(req.params.id)) return res.sendStatus(404);
    res.set("X-Content-Type-Options", "nosniff");
    res.download(req.params.id, { root: STORE_DIR }, (err) => {
      if (err && !res.headersSent) next(Object.assign(err, { status: err.status ?? 404 }));
    }); // 실행 대신 다운로드
  });

  return router;
}
