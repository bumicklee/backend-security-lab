// ⚠️ 의도적으로 취약한 실습 코드 — 운영에 쓰지 마세요
// LAB_MODE=vulnerable + HOST=127.0.0.1 일 때만 /lab 아래에 등록된다.
// 2-7 프로토타입 오염: 본문 전체를 깊게 병합한다. key 가 "__proto__" 라면?
import { Router } from "express";

function merge(target, src) {
  for (const key in src) {
    if (typeof src[key] === "object" && src[key] !== null) {
      target[key] ??= {};
      merge(target[key], src[key]); // key가 "__proto__"라면?
    } else target[key] = src[key];
  }
  return target;
}

export function labSettingsRoutes() {
  const router = Router();
  // 세션 대신 고정 사용자 (실습 단순화). 쿠키 sid=alice-session 은 형식만 맞춘 것.
  const sessions = { "alice-session": { username: "alice", settings: { theme: "dark" } } };
  const userOf = (req) => sessions[/sid=([\w-]+)/.exec(req.get("cookie") ?? "")?.[1]] ?? sessions["alice-session"];

  router.patch("/me/settings", (req, res) => {
    const user = userOf(req);
    merge(user.settings, req.body);
    res.json(user.settings);
  });

  router.get("/admin/users", (req, res) => {
    const user = userOf(req);
    if (!user.isAdmin) return res.sendStatus(403); // 원형이 오염되면 alice 도 통과
    res.json([
      { id: 1, email: "admin@example.com", role: "ADMIN" },
      { id: 2, email: "alice@example.com", role: "USER" },
    ]);
  });

  // 실습 후 정리: Object.prototype 에 붙은 열거 가능한 키를 지운다 (내장 속성은 열거 불가)
  router.post("/reset-prototype", (req, res) => {
    const removed = [];
    for (const key in Object.prototype) {
      removed.push(key);
      delete Object.prototype[key];
    }
    res.json({ removed });
  });

  return router;
}
