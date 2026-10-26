package board.support;

/**
 * 로그에 개인정보를 남기지 않기 위한 마스킹 도구 (0-3 · 5-4).
 * Express {@code lib/mask.js} 와 같은 모양으로 가린다: 010-****-5678 · ****-****-****-1111 · a***@example.com
 */
public final class Mask {
    private Mask() {}

    /** 010-1234-5678 → 010-****-5678 */
    public static String phone(String phone) {
        if (phone == null) return null;
        String digits = phone.replaceAll("\\D", "");
        if (digits.length() < 10) return "***";
        return digits.substring(0, 3) + "-****-" + digits.substring(digits.length() - 4);
    }

    /** 카드·계좌 번호는 끝 4자리만: ****-****-****-1111 */
    public static String last4(String number) {
        if (number == null) return null;
        String digits = number.replaceAll("\\D", "");
        if (digits.length() < 4) return "****";
        return "****-****-****-" + digits.substring(digits.length() - 4);
    }

    /** alice@example.com → a***@example.com */
    public static String email(String email) {
        if (email == null) return null;
        int at = email.indexOf('@');
        if (at <= 0 || at == email.length() - 1) return "***";
        return email.charAt(0) + "***" + email.substring(at);
    }
}
