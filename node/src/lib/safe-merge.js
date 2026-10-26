// 깊은 병합이 꼭 필요할 때: 원형을 바꾸는 키는 건너뛴다 (2-7 수업 체크리스트).
const BLOCKED = new Set(["__proto__", "constructor", "prototype"]);

export function safeMerge(target, src) {
  for (const key of Object.keys(src)) {
    if (BLOCKED.has(key)) continue;
    const v = src[key];
    if (typeof v === "object" && v !== null && !Array.isArray(v)) {
      if (!Object.hasOwn(target, key) || typeof target[key] !== "object" || target[key] === null) {
        target[key] = Object.create(null);
      }
      safeMerge(target[key], v);
    } else {
      target[key] = v;
    }
  }
  return target;
}
