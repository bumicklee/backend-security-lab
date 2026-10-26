#!/usr/bin/env bash
# 6-8 지속성 헌팅: 알림이 없어도 먼저 찾아본다. 읽기만 하고 아무것도 지우거나 고치지 않는다.
#   sudo ./hunt-persistence.sh [일수(기본 14)]
# 찾은 것은 바로 지우지 말고(증거), 해시·사본을 남긴 뒤 6-9 절차로 넘긴다.
# ATT&CK: T1098.004(SSH 키) · T1053.003(cron) · T1543.002(systemd) · T1564.001(숨김 파일)
set -u
DAYS=${1:-14}
section() { printf '\n== %s\n' "$1"; }

section "외부로 나가는 연결 (Local · Peer · Process)"
# state 필터를 쓰면 State 열이 빠진다 → $3 $4 $5. -H 로 머리글 제거
ss -Htnp state established | awk '{print $3, $4, $5}' | column -t

section "최근 ${DAYS}일 안에 바뀐 authorized_keys (T1098.004)"
# 화면 s5 와 같은 형식: 바뀐 날짜 · 분 단위 시각 · 소유자 · 경로
find / -xdev -name authorized_keys -mtime "-$DAYS" -printf '%TF %TR %u %p\n' 2>/dev/null
grep -h 'Accepted publickey for root' /var/log/auth.log 2>/dev/null | tail -5

section "사용자별 crontab (T1053.003)"
cut -d: -f1 /etc/passwd | while read -r u; do
  crontab -l -u "$u" 2>/dev/null | sed "s/^/$u: /"
done
section "/etc/cron.d (생성 날짜를 키 파일 변경일과 비교)"
ls -l --time-style=long-iso /etc/cron.d   # 날짜와 분 단위 시각 (서버 시계 UTC)

section "최근 ${DAYS}일 안에 바뀐 systemd 유닛과 활성화 링크 (T1543.002)"
find /etc/systemd /usr/lib/systemd /root/.config/systemd /home/*/.config/systemd \
  \( -type f -o -type l \) -mtime "-$DAYS" 2>/dev/null

section "임시 폴더의 실행 파일 (T1564.001)"
find /tmp /var/tmp /dev/shm -type f -perm -u+x 2>/dev/null

section "삭제된 채 실행 중인 프로세스"
# 원본은 /proc/<pid>/exe 로 꺼낼 수 있다: sha256sum /proc/<pid>/exe; cp /proc/<pid>/exe /mnt/ir/
find /proc -maxdepth 2 -name exe -type l -printf '%p -> %l\n' 2>/dev/null | grep '(deleted)'
