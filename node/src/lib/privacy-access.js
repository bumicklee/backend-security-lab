// 5-4 접속기록: 누가 · 언제 · 어디서 · 누구의 · 무엇을 (「개인정보의 안전성 확보조치 기준」 다섯 항목)
// 식별자(actorId) · 접속일시(at) · 접속지 정보(from) · 처리한 정보주체(subjects) · 수행업무(task)
// 처리한 정보주체는 핸들러가 res.locals.subjectIds 에 넣는다. 건수(count)도 남겨 대량 조회를 찾는다.
export function createPrivacyAccess(auditLog, logger) {
  return (task) => (req, res, next) => {
    res.on("finish", () => auditLog.write({
      actorId: req.user.sub,                   // 누가: 접속한 자의 식별자
      at: new Date().toISOString(),            // 언제: 접속 일시
      from: req.ip,                            // 어디서: 접속지 정보
      subjects: res.locals.subjectIds ?? [],   // 누구의: 처리한 정보주체
      task, status: res.statusCode,            // 무엇을: 조회·수정·다운로드
      count: res.locals.subjectIds?.length ?? 0 })
      .catch((e) => logger?.error("privacy access log failed", { err: e.message })));
    next(); };
}
