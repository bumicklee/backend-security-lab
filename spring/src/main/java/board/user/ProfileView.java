package board.user;

/** 4-1 응답 DTO: 내보낼 필드만. 엔티티에 필드가 늘어도 응답은 바뀌지 않는다. */
public record ProfileView(String username,
    String nickname, String bio) {

    static ProfileView of(AppUser u) {
        return new ProfileView(u.getUsername(), u.getNickname(), u.getBio());
    }
}
