#!/bin/sh
PATH=/usr/local/bin:/usr/bin:/bin   # cron 기본 PATH에는 /usr/local/bin(trivy)이 없다
LOG=/var/log/kev_watch.log
trivy rootfs -q --scanners vuln -f json -o /var/tmp/host.json / \
  || { echo "trivy 스캔 실패" > "$LOG"; /opt/notify.sh "$LOG"; exit 1; }
python3 /opt/kev_watch.py /var/tmp/host.json \
  --state /var/lib/kev_watch/state.json > "$LOG" 2>&1
rc=$?
[ "$rc" -ne 0 ] && /opt/notify.sh "$LOG"    # 2 = P1 있음, 1 = 실행 오류
exit "$rc"
