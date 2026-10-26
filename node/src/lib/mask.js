// 로그 마스킹 (0-3 수업). 로그에는 필요한 것만, 개인정보는 가려서 남긴다.
export const Mask = {
  // 010-1234-5678 → 010-****-5678
  phone(v) {
    const d = String(v ?? "").replace(/\D/g, "");
    if (d.length < 10) return "***";
    return `${d.slice(0, 3)}-****-${d.slice(-4)}`;
  },
  // 카드번호 → ****-****-****-1111
  last4(v) {
    const d = String(v ?? "").replace(/\D/g, "");
    if (d.length < 4) return "****";
    return `****-****-****-${d.slice(-4)}`;
  },
  // alice@example.com → a***@example.com
  email(v) {
    const [local, domain] = String(v ?? "").split("@");
    if (!local || !domain) return "***";
    return `${local[0]}***@${domain}`;
  },
};
