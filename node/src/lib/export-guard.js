// 5-5 반출 감시: 관리자별 "오늘 누적 내보내기 건수"를 세고, 기준을 넘으면 알린다.
// 실습용 메모리 카운터. 서버가 여러 대면 Redis INCRBY + 만료(하루)로 같은 일을 한다.
export function createExportCounter(now = () => Date.now()) {
  const m = new Map(); // "sub:YYYY-MM-DD" → 건수
  return {
    async add(user, n) {
      const day = new Date(now()).toISOString().slice(0, 10);
      const key = `${user.sub}:${day}`;
      for (const k of m.keys()) if (!k.endsWith(day)) m.delete(k); // 지난 날짜 정리
      const total = (m.get(key) ?? 0) + n;
      m.set(key, total);
      return total;
    },
  };
}

// 기본 알림: 경고 로그 한 줄 (운영에서는 보안 담당 채널·SIEM 으로)
export const logAlert = (logger) => (event, fields = {}) => logger.warn("alert", { event, ...fields });
