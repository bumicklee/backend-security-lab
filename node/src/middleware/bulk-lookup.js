// 6-2 계정별 조회량 감시. 키는 IP 가 아니라 "계정 + 날짜(UTC)".
// 300번째 요청에서 알림(bulk_lookup) 한 번, 1000 번을 넘으면 429, 처음 막을 때(1001번째) 차단 이벤트(bulk_block) 한 번.
// 기준값은 예시다. 실제 사용자 분포를 몇 주 지켜본 뒤 정한다.
export const ALERT_AT = 300;
export const BLOCK_OVER = 1000;

export const bulkLookupGuard = (redis, events) => async (req, res, next) => {
  const user = req.user.sub;                                       // 화면: req.user.id (이 저장소의 JWT 는 sub)
  const key = `lookup:${user}:${new Date().toISOString().slice(0, 10)}`;
  const n = await redis.incr(key);                                 // 계정별 하루 조회 수
  if (n === 1) await redis.expire(key, 2 * 86400);
  if (n === ALERT_AT) events.emit("bulk_lookup", { user, ip: req.ip, count: n });       // 알림은 한 번
  if (n === BLOCK_OVER + 1) events.emit("bulk_block", { user, ip: req.ip, count: n });  // 차단도 기록
  if (n > BLOCK_OVER) return res.status(429).json({ error: "review_required" });
  next();
};
