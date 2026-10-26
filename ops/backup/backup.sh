#!/usr/bin/env bash
# 5-6 매일 03:00 앱 서버에서 실행: 설정·업로드 폴더 + DB 일관 덤프를 restic 저장소(암호화)로 보낸다.
# 이 서버의 자격 증명은 "쓰기·읽기만" (iam-backup-writer.json) → 서버를 장악해도 잠긴 백업 버전은 못 지운다.
#
# 필요한 환경 변수 (예: /etc/restic/env, 권한 600)
#   RESTIC_REPOSITORY     s3:https://s3.example.com/board-backup
#   RESTIC_PASSWORD_FILE  /etc/restic/password  (권한 600, 시크릿 매니저에서 받아 둔다)
#   BACKUP_PATHS          백업할 폴더 (기본: /etc /var/lib/app)
#   BACKUP_DB             pg_dump 대상 DB 이름 (기본: board). 접속 정보는 PGHOST·PGUSER·~/.pgpass
#   BACKUP_HOST           스냅숏에 남길 호스트 이름 (기본: web1). check.sh·restore-drill.sh 가 같은 값으로 찾는다
set -euo pipefail

: "${RESTIC_REPOSITORY:?RESTIC_REPOSITORY 가 필요합니다}"
: "${RESTIC_PASSWORD_FILE:?RESTIC_PASSWORD_FILE 이 필요합니다 (비밀번호를 명령줄·환경 변수에 직접 두지 않는다)}"
BACKUP_DB="${BACKUP_DB:-board}"
# restic 은 기본으로 이 서버의 hostname 을 스냅숏에 남긴다. 실습 VM 처럼 hostname 이 web1 이 아니면
# check.sh(--host web1)가 스냅숏을 못 찾으므로, 세 스크립트가 같은 BACKUP_HOST 를 쓰게 한다
BACKUP_HOST="${BACKUP_HOST:-web1}"
read -r -a paths <<< "${BACKUP_PATHS:-/etc /var/lib/app}"

log() { printf '%s backup: %s\n' "$(date -Is)" "$*"; }

# 1) 설정·업로드 파일은 폴더째
log "files: ${paths[*]} (host=${BACKUP_HOST})"
restic backup "${paths[@]}" --exclude-caches --tag files --host "${BACKUP_HOST}"

# 2) DB 는 데이터 파일 복사가 아니라 일관된 덤프. pg_dump 를 restic 이 직접 실행하므로
#    덤프가 실패(0 이 아닌 종료 코드)하면 스냅숏을 만들지 않는다 (파이프는 잘린 덤프가 저장될 수 있다)
log "db: ${BACKUP_DB}"
restic backup --tag db --host "${BACKUP_HOST}" \
  --stdin-from-command --stdin-filename board.dump -- pg_dump -Fc "${BACKUP_DB}"

log "done"
