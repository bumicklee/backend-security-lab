// 내 설정 변경 (2-7 프로토타입 오염: 받을 키를 스키마로 고정)
import { Router } from "express";
import { z } from "zod";

// strictObject: __proto__·constructor 같은 모르는 키는 400 (.partial() 뒤에도 유지)
const Settings = z
  .strictObject({
    theme: z.enum(["light", "dark"]),
    lang: z.enum(["ko", "en"]),
  })
  .partial();

export function settingsRoutes({ stores }, auth) {
  const router = Router();
  const settingsOf = (user) => {
    if (!stores.settings.has(user.sub)) stores.settings.set(user.sub, { theme: "light", lang: "ko" });
    return stores.settings.get(user.sub);
  };

  router.get("/me/settings", auth.requireAuth(), (req, res) => res.json(settingsOf(req.user)));

  router.patch("/me/settings", auth.requireAuth(), (req, res) => {
    const r = Settings.safeParse(req.body);
    if (!r.success) return res.sendStatus(400);
    const settings = settingsOf(req.user);
    Object.assign(settings, r.data);
    res.json(settings);
  });

  return router;
}
