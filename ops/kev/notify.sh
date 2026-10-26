#!/bin/sh
# /opt/notify.sh : kev_daily.sh가 넘긴 로그 파일을 메신저 웹훅으로 보낸다 (예시, 7-3)
#   usage: notify.sh /var/log/kev_watch.log
# 웹훅 주소는 코드나 cron에 쓰지 않는다. 비밀 저장소에서 받아 root만 읽는 파일에 둔다(5-3).
#   /etc/kev_watch/webhook_url  (root:root, 권한 600)  또는 KEV_WEBHOOK_FILE 환경 변수로 지정
# 본문은 Slack 호환 {"text": "..."} 형식이다. 다른 메신저는 JSON 모양만 바꾼다.
set -eu
PATH=/usr/local/bin:/usr/bin:/bin
LOG="$1"
URL_FILE="${KEV_WEBHOOK_FILE:-/etc/kev_watch/webhook_url}"
BODY=$(mktemp)
trap 'rm -f "$BODY"' EXIT
{ printf '[kev_watch] %s\n' "$(hostname)"; head -n 40 "$LOG"; } \
  | python3 -c 'import json, sys; print(json.dumps({"text": sys.stdin.read()}))' > "$BODY"
# 주소가 프로세스 목록(ps)에 보이지 않게 curl 설정을 표준 입력으로 넘긴다
printf 'url = "%s"\n' "$(cat "$URL_FILE")" \
  | curl -fsS -m 10 -K - -H 'Content-Type: application/json' --data-binary @"$BODY" -o /dev/null
