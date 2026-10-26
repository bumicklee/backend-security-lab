#!/usr/bin/env bash
# 5-6 보존 정책 적용(오래된 스냅숏 정리). 앱 서버가 아니라 관리 서버(ops)에서, 삭제 권한이 있는 별도 자격 증명으로 실행한다.
# 객체 잠금(COMPLIANCE, 30일) 기간 안의 데이터는 이 명령으로도 지워지지 않는다.
set -euo pipefail

: "${RESTIC_REPOSITORY:?RESTIC_REPOSITORY 가 필요합니다}"
: "${RESTIC_PASSWORD_FILE:?RESTIC_PASSWORD_FILE 이 필요합니다}"

# 일 7 · 주 4 · 월 6 개를 남기고 나머지는 정리
restic forget --prune \
  --keep-daily "${KEEP_DAILY:-7}" \
  --keep-weekly "${KEEP_WEEKLY:-4}" \
  --keep-monthly "${KEEP_MONTHLY:-6}"
