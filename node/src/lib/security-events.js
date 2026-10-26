// 6-2 보안 이벤트 로그 (security.log). 한 줄에 JSON 하나, 첫 필드는 ISO 8601 UTC 시각 "ts".
// fail2ban 은 줄에서 시각을 찾아 findtime 을 계산하므로 시각이 없으면 매칭되지 않는다.
// 필드 순서는 ts → event → user → ip → … (ops/fail2ban/filter.d/board-security.conf 의 failregex 와 맞춘다)
import { appendFile, mkdir } from "node:fs/promises";
import path from "node:path";

export function createSecurityEvents({ file = "", logger, now = () => new Date() } = {}) {
  const rows = [];   // 파일이 없을 때(테스트·메모리 모드) 확인용
  let ready;
  const write = async (line) => {
    ready ??= mkdir(path.dirname(file), { recursive: true });
    await ready;
    await appendFile(file, line + "\n", { flag: "a", mode: 0o640 });
  };
  return {
    // events.emit("bulk_lookup", { user, ip, count }) — 동기로 부르고, 파일 쓰기 실패는 로그로만 남긴다
    emit(event, fields = {}) {
      const entry = { ts: now().toISOString().replace(/\.\d{3}Z$/, "Z"), event, ...fields };
      rows.push(entry);
      logger?.warn("security_event", entry);
      if (file) write(JSON.stringify(entry)).catch((e) => logger?.error("security log write failed", { err: e.message }));
      return entry;
    },
    entries: () => [...rows],
  };
}
