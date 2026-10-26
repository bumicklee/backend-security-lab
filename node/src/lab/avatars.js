// ⚠️ 의도적으로 취약한 실습 코드 — 운영에 쓰지 마세요
// LAB_MODE=vulnerable + HOST=127.0.0.1 일 때만 /lab 아래에 등록된다.
// 2-5 파일 업로드: 클라이언트가 보낸 MIME 만 믿고, 원본 이름 그대로 웹 루트(public)에 저장한다.
import { mkdirSync } from "node:fs";
import path from "node:path";
import express, { Router } from "express";
import multer from "multer";

export function labAvatarRoutes({ config }) {
  const router = Router();
  const uploadsDir = path.join(config.labPublicDir, "uploads");
  mkdirSync(uploadsDir, { recursive: true });

  const upload = multer({
    storage: multer.diskStorage({
      destination: uploadsDir,
      filename: (req, f, cb) =>
        cb(null, f.originalname),
    }),
    fileFilter: (req, f, cb) => cb(null,
      f.mimetype.startsWith("image/")),
  });
  router.use(express.static(config.labPublicDir)); // /lab/uploads/<원본 이름>

  router.post("/avatars", upload.single("file"), (req, res) => {
    if (!req.file) return res.sendStatus(400);
    res.json({ url: `/lab/uploads/${req.file.originalname}` });
  });

  return router;
}
