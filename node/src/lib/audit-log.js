// 5-4 개인정보 접속기록 저장소. 일반 접근 로그(logger.accessLog)와 분리하고, 쓰기(append)만 연다.
// 고치거나 지우는 메서드는 없다. 운영에서는 수정·삭제가 막힌 저장소(객체 잠금 버킷, 권한을 INSERT 만 준 테이블 등)로 보낸다.
import { appendFile, mkdir } from "node:fs/promises";
import path from "node:path";

// 파일: 한 줄에 JSON 하나 (O_APPEND). 파일 권한 640
export function createFileAuditLog(file) {
  let ready;
  return {
    async write(entry) {
      ready ??= mkdir(path.dirname(file), { recursive: true });
      await ready;
      await appendFile(file, JSON.stringify(entry) + "\n", { flag: "a", mode: 0o640 });
    },
  };
}

// 테스트·메모리 모드: 읽기는 복사본만 돌려준다
export function createMemoryAuditLog() {
  const rows = [];
  return {
    async write(entry) {
      rows.push(Object.freeze({ ...entry }));
    },
    entries: () => [...rows],
  };
}
