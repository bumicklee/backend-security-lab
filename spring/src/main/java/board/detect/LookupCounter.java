package board.detect;

import java.time.Duration;

/**
 * 6-2 계정별 조회 수 카운터. 화면의 {@code StringRedisTemplate} 과 같은 모양(increment · expire).
 * 이 저장소는 Redis 없이도 뜨도록 메모리 구현({@link MemoryLookupCounter})을 쓴다.
 * 서버가 여러 대면 {@code redis.opsForValue().increment(key)} + {@code redis.expire(key, ttl)} 로 바꾼다.
 */
public interface LookupCounter {
    long increment(String key);

    void expire(String key, Duration ttl);
}
