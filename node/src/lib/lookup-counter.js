// 6-2 계정별 조회 수 카운터. Redis 클라이언트(node-redis)와 같은 incr·expire 모양.
// RATE_LIMIT_REDIS_URL 이 있으면 Redis 를 그대로 쓰고(서버 여러 대가 같은 숫자를 센다),
// 없으면(테스트·단일 서버) 이 메모리 구현을 쓴다.
export function createMemoryCounter(now = () => Date.now()) {
  const m = new Map(); // key → { n, exp }
  const live = (key) => {
    const e = m.get(key);
    if (e && e.exp && e.exp <= now()) {
      m.delete(key);
      return undefined;
    }
    return e;
  };
  return {
    async incr(key) {
      const e = live(key) ?? { n: 0, exp: 0 };
      e.n += 1;
      m.set(key, e);
      return e.n;
    },
    async expire(key, seconds) {
      const e = live(key);
      if (!e) return 0;
      e.exp = now() + seconds * 1000;
      return 1;
    },
    // 테스트용: 오늘 이미 n 번 조회한 상태로 만든다
    async set(key, n) {
      m.set(key, { n, exp: now() + 2 * 86400_000 });
    },
  };
}
