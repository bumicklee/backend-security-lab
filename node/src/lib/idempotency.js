// Idempotency-Key 미들웨어 (모바일 앱 연동 API, 4-4 수업).
// 같은 키 + 같은 본문 → 처음 응답을 그대로 돌려준다. 같은 키 + 다른 본문 → 422.
import { createHash } from "node:crypto";

const KEY = /^[A-Za-z0-9_-]{8,64}$/;

export function idempotency(store, { ttlSec = 24 * 60 * 60 } = {}) {
  return (req, res, next) => {
    const key = req.get("Idempotency-Key");
    if (key === undefined) return next(); // 이 실습에서는 선택 헤더
    if (!KEY.test(key)) return res.status(400).json({ error: "invalid Idempotency-Key" });

    const id = `${req.user?.sub ?? "anon"}:${req.method}:${req.path}:${key}`;
    const fingerprint = createHash("sha256").update(JSON.stringify(req.body ?? null)).digest("hex");
    const saved = store.get(id);
    if (saved) {
      if (saved.fingerprint !== fingerprint) {
        return res.status(422).json({ error: "같은 Idempotency-Key 로 다른 요청을 보냈습니다" });
      }
      if (saved.state === "pending") return res.status(409).json({ error: "처리 중인 요청입니다" });
      res.set("Idempotent-Replayed", "true");
      return res.status(saved.status).json(saved.body);
    }

    store.set(id, { state: "pending", fingerprint }, ttlSec); // 먼저 선점해 동시 재시도를 막는다
    const json = res.json.bind(res);
    res.json = (body) => {
      if (res.statusCode >= 500) store.delete(id); // 서버 오류면 키를 풀어 재시도를 허용
      else store.set(id, { state: "done", fingerprint, status: res.statusCode, body }, ttlSec);
      return json(body);
    };
    next();
  };
}
