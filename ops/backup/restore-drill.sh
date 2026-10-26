#!/usr/bin/env bash
# 5-6 분기 복구 훈련: 별도 서버(restore-test)에서 최신 스냅숏을 실제로 되살리고 걸린 시간을 기록한다.
# 운영 DB 가 아니라 훈련용 DB(board_restore_test)에 복원한다. 결과(시간·건수)를 훈련 기록에 남긴다.
set -euo pipefail

: "${RESTIC_REPOSITORY:?RESTIC_REPOSITORY 가 필요합니다}"
: "${RESTIC_PASSWORD_FILE:?RESTIC_PASSWORD_FILE 이 필요합니다}"
BACKUP_HOST="${BACKUP_HOST:-web1}"
TARGET="${RESTORE_TARGET:-/srv/restore-test}"
DRILL_DB="${DRILL_DB:-board_restore_test}"

if [[ "${DRILL_DB}" != *restore_test* ]]; then
  echo "훈련용 DB 이름에는 restore_test 가 들어가야 합니다 (운영 DB 덮어쓰기 방지): ${DRILL_DB}" >&2
  exit 1
fi

start=$(date +%s)
restic snapshots --latest 1 --host "${BACKUP_HOST}"

files_id="$(restic snapshots --latest 1 --host "${BACKUP_HOST}" --tag files --json | jq -r '.[0].short_id')"
db_id="$(restic snapshots --latest 1 --host "${BACKUP_HOST}" --path /board.dump --json | jq -r '.[0].short_id')"

# 1) 파일 복원
mkdir -p "${TARGET}"
restic restore "${files_id}" --target "${TARGET}"

# 2) DB 복원 (훈련용 DB 를 새로 만든 뒤)
dropdb --if-exists "${DRILL_DB}"
createdb "${DRILL_DB}"
restic dump "${db_id}" /board.dump | pg_restore --no-owner -d "${DRILL_DB}"

# 3) 되살아났는지 확인: 게시글 수
posts="$(psql -d "${DRILL_DB}" -tAc "select count(*) from posts")"
elapsed=$(( $(date +%s) - start ))
printf 'restore drill: host=%s files=%s db=%s posts=%s elapsed=%ss\n' \
  "${BACKUP_HOST}" "${files_id}" "${db_id}" "${posts}" "${elapsed}"
