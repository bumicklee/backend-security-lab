// 링크 미리보기 (2-6 SSRF: 스킴·호스트 허용 목록, 해석된 IP 검사, 리다이렉트 금지)
import { Router } from "express";
import { isPublic } from "../lib/net.js";
import { toPreview } from "../lib/preview-html.js";

export function previewRoutes({ config, lookup, fetch }) {
  const router = Router();
  const ALLOWED = new Set(config.previewAllowedHosts);

  router.get("/preview", async (req, res) => {
    const u = URL.parse(String(req.query.url));
    if (!u || u.protocol !== "https:" || !ALLOWED.has(u.hostname)) return res.sendStatus(400);
    try {
      const ips = await lookup(u.hostname, { all: true }); // 해석된 IP
      if (ips.some((a) => !isPublic(a.address))) return res.sendStatus(400);
      const r = await fetch(u, {
        redirect: "error", // 리다이렉트 금지
        signal: AbortSignal.timeout(3000),
      });
      res.json(toPreview((await r.text()).slice(0, 200_000)));
    } catch {
      // 조회 실패·리다이렉트·시간 초과 모두 같은 응답 (내부 정보 노출 금지)
      res.sendStatus(502);
    }
    // 남은 위험: 검사 후 DNS 가 바뀌는 리바인딩. 운영에서는 이그레스 프록시를 거친다.
  });

  return router;
}
