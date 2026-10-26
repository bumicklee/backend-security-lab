// 관리자 회원 기능
// 5-2 전화번호 검색은 블라인드 인덱스로 (WHERE phone_idx = ?)
// 5-4 회원 정보를 볼 때마다 접속기록 다섯 항목 (privacyAccess)
// 5-5 내보내기: 필요한 컬럼만, 1,000건씩 나눠서, 오늘 누적 건수를 세며
import { Router } from "express";
import { z } from "zod";
import { Mask } from "../lib/mask.js";
import { createPrivacyAccess } from "../lib/privacy-access.js";

const PAGE = 1000;
// 내보낼 수 있는 컬럼 허용 목록 (암호문·인덱스·연락처는 목록에 없다)
const EXPORTABLE = ["id", "nickname", "email", "createdAt"];
const ExportQuery = z.object({
  page: z.coerce.number().int().min(0).max(1_000_000).default(0),
  fields: z.string().default("id,nickname")
    .transform((s) => s.split(",").map((f) => f.trim()).filter(Boolean))
    .pipe(z.array(z.enum(EXPORTABLE)).min(1).max(EXPORTABLE.length)),
});
const PhoneQuery = z.object({ phone: z.string().regex(/^[0-9-]{9,13}$/) });

const ids = (rows) => rows.map((r) => r.id);
const page = (req) => req.query.page;

export function memberRoutes({ prisma, members, stores, auditLog, alert, logger, config }, auth) {
  const router = Router();
  const privacyAccess = createPrivacyAccess(auditLog, logger);
  // 로그인 → 접속기록(권한이 없어 거절된 시도도 남는다) → 관리자 확인
  const admin = (task) => [auth.requireAuth(), privacyAccess(task), auth.requireRole("ADMIN")];
  const addExport = (user, n) => stores.exports.add(user, n);

  const view = async (m) => ({
    id: m.id, nickname: m.nickname, email: Mask.email(m.email),
    phone: Mask.phone(await members.phoneOf(m)) }); // 복호화해도 화면에는 가려서

  // 5-2 같은 번호로 찾기: 평문 번호 대신 HMAC 인덱스로 조회
  router.get("/admin/members", ...admin("회원 검색"), async (req, res) => {
    const r = PhoneQuery.safeParse(req.query);
    if (!r.success) return res.sendStatus(400);
    const rows = await members.findByPhone(r.data.phone);
    res.locals.subjectIds = ids(rows);
    res.json(await Promise.all(rows.map(view)));
  });

  // 5-5 관리자 내보내기
  router.get("/admin/members/export", ...admin("회원 다운로드"), async (req, res) => {
    const r = ExportQuery.safeParse({ page: page(req), fields: req.query.fields });
    if (!r.success) return res.sendStatus(400);
    const select = Object.fromEntries(["id", ...r.data.fields].map((f) => [f, true]));
    const rows = await prisma.member.findMany({
      where: { withdrawnAt: null },
      select,
      orderBy: { id: "asc" }, take: PAGE,
      skip: r.data.page * PAGE });
    res.locals.subjectIds = ids(rows);
    const total = await addExport(req.user,
      rows.length);   // 오늘 누적 건수
    if (total > config.exportAlertThreshold)
      alert("bulk-export", { actorId: req.user.sub, total });
    res.json(rows); });

  // 5-4 회원 조회
  router.get("/admin/members/:id", ...admin("회원 조회"), async (req, res) => {
    const id = Number(req.params.id);
    if (!Number.isSafeInteger(id) || id < 1) return res.sendStatus(400);
    const m = await members.findById(id);
    if (!m) return res.sendStatus(404);
    res.locals.subjectIds = [m.id];
    res.json(await view(m));
  });

  return router;
}
