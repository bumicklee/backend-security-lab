#!/usr/bin/env bash
# 5-6 매주 일요일 자동 실행(관리 서버): 저장소 무결성 + 백업 신선도 감시.
# 실패하면 0 이 아닌 코드로 끝난다 → cron·systemd 타이머의 실패 알림으로 연결한다.
set -euo pipefail

: "${RESTIC_REPOSITORY:?RESTIC_REPOSITORY 가 필요합니다}"
: "${RESTIC_PASSWORD_FILE:?RESTIC_PASSWORD_FILE 이 필요합니다}"
BACKUP_HOST="${BACKUP_HOST:-web1}"
MAX_AGE_HOURS="${MAX_AGE_HOURS:-26}"
SUBSET="${CHECK_SUBSET:-5%}"

# 1) 데이터 일부를 실제로 읽어 깨진 조각이 없는지 확인
restic check --read-data-subset="${SUBSET}"

# 2) 가장 최근 DB 덤프 스냅숏이 26시간보다 오래되면 실패 (백업이 조용히 멈춘 경우)
latest="$(restic snapshots --latest 1 --host "${BACKUP_HOST}" --path /board.dump --json | jq -r '.[0].time // empty')"
if [[ -z "${latest}" ]]; then
  echo "ALERT: ${BACKUP_HOST} 의 DB 덤프 스냅숏이 없습니다" >&2
  exit 2
fi
# GNU date(리눅스)는 restic 의 RFC 3339 시각(소수점 이하 포함)을 그대로 읽는다
age_hours=$(( ( $(date +%s) - $(date -d "${latest}" +%s) ) / 3600 ))
echo "latest db snapshot: ${latest} (${age_hours}h ago)"
if (( age_hours > MAX_AGE_HOURS )); then
  echo "ALERT: 마지막 DB 백업이 ${age_hours}시간 전입니다 (기준 ${MAX_AGE_HOURS}시간)" >&2
  exit 3
fi
