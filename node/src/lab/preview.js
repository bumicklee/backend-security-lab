// ⚠️ 의도적으로 취약한 실습 코드 — 운영에 쓰지 마세요
// LAB_MODE=vulnerable + HOST=127.0.0.1 일 때만 /lab 아래에 등록된다.
// 2-6 SSRF: 사용자가 준 주소로 서버가 직접 요청한다.
// 진짜 클라우드 메타데이터 대신, 루프백에서만 응답하는 가짜 메타데이터(/lab/fake-metadata)를 노려 보자.
import { Router } from "express";
import { toPreview } from "../lib/preview-html.js";

export function labPreviewRoutes() {
  const router = Router();

  router.get("/preview", async (req, res) => {
    try {
      const r = await fetch(req.query.url);
      const html = await r.text();
      res.json(toPreview(html));
    } catch (e) {
      res.status(502).json({ error: String(e.message) });
    }
  });

  // "내부망에만 있는" 가짜 메타데이터 서버 흉내. 값은 모두 AWS 문서의 예시 형식인 가짜 값.
  router.get("/fake-metadata/latest/meta-data/iam/security-credentials/:role", (req, res) => {
    if (!isLoopback(req.socket.remoteAddress)) return res.sendStatus(403);
    res.type("text").send(
      JSON.stringify({
        AccessKeyId: "ASIAEXAMPLEEXAMPLE",
        SecretAccessKey: "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY",
        Token: "IQoJb3JpZ2luX2VjEXAMPLE",
        Expiration: "2026-10-25T09:41:00Z",
      }),
    );
  });
  router.get("/fake-metadata/latest/meta-data/iam/security-credentials/", (req, res) => {
    if (!isLoopback(req.socket.remoteAddress)) return res.sendStatus(403);
    res.type("text").send("app-role");
  });

  return router;
}

function isLoopback(addr = "") {
  return addr === "::1" || addr.startsWith("127.") || addr.startsWith("::ffff:127.");
}
