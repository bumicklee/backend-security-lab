package board.user;

import jakarta.validation.constraints.Size;

/**
 * 4-1 요청 DTO: 사용자가 바꿀 수 있는 필드만. role·passwordHash 같은 필드는 아예 없다.
 * 모르는 필드는 역직렬화에서 거절된다 (spring.jackson.deserialization.fail-on-unknown-properties=true → 400).
 */
public record ProfileUpdate(
    @Size(max = 30) String nickname,
    @Size(max = 200) String bio) {}
