// 5-4 파기 배치: 삭제 표시가 아니라, 보존 기간이 지나면 지운다. 매일 새벽 03:30 (server.js 에서 예약)
// - 탈퇴 회원: 탈퇴 후 MEMBER_RETENTION_DAYS 가 지나면 행을 지운다
// - 주문: 다른 법(전자상거래법 등)이 정한 보존 기간(ORDER_RETENTION_DAYS)이 지나면 구매자를 익명화한다
const DAY = 86_400_000;

export async function purgeExpired({ prisma, logger, config }, now = new Date()) {
  const cutoff = new Date(now.getTime() - config.memberRetentionDays * DAY);
  const { count: n } = await prisma.member.deleteMany({ where: { withdrawnAt: { lt: cutoff } } });
  const orderCutoff = new Date(now.getTime() - config.orderRetentionDays * DAY);
  const { count: anonymized } = await prisma.order.updateMany({
    where: { createdAt: { lt: orderCutoff } }, data: { userId: "anonymized" } });
  logger.info("purge", { members: n, orders: anonymized });
  return { members: n, orders: anonymized };
}

// 매일 hh:mm(서버 시간)에 fn 실행. 프로세스 종료를 막지 않도록 unref
export function scheduleDaily(hhmm, fn, { now = () => new Date(), setTimer = setTimeout } = {}) {
  const [h, m] = hhmm.split(":").map(Number);
  let timer;
  const arm = () => {
    const t = now();
    const next = new Date(t);
    next.setHours(h, m, 0, 0);
    if (next <= t) next.setDate(next.getDate() + 1);
    timer = setTimer(async () => {
      try { await fn(); } finally { arm(); }
    }, next - t);
    timer.unref?.();
  };
  arm();
  return () => clearTimeout(timer);
}
