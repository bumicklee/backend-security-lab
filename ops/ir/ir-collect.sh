#!/usr/bin/env bash
# 6-9 휘발성 정보 수집 스크립트. 사고 전에 미리 모든 서버의 /opt/ir/ir-collect.sh 로 배포해 둔다.
#   sudo /opt/ir/ir-collect.sh            # 결과: /mnt/ir/<호스트>-<UTC 시각>/
# 수집 순서는 RFC 3227 의 휘발성 순서: 프로세스·연결 → 메모리 → 로그·디스크.
# 결과는 외부 저장소(IR_BASE, 기본 /mnt/ir)에만 쓴다. 증거가 있는 로컬 디스크에는 쓰지 않는다.
# 준비물(미리 설치): avml(메모리 덤프, https://github.com/microsoft/avml), lsof
# 실행 전에 대응 기록에 한 줄: echo "$(date -u +%FT%TZ) run ir-collect (alice)" >> /mnt/ir/IR-YYYY-MMDD.log
set -u
umask 077

IR_BASE=${IR_BASE:-/mnt/ir}
if [ "$(id -u)" -ne 0 ]; then echo "root 로 실행하세요 (sudo)" >&2; exit 1; fi
if ! mountpoint -q "$IR_BASE" && [ "${IR_ALLOW_LOCAL:-0}" != 1 ]; then
  echo "$IR_BASE 가 외부 저장소로 마운트되어 있지 않습니다 (훈련용이면 IR_ALLOW_LOCAL=1)" >&2; exit 1
fi

OUT=$IR_BASE/$(hostname)-$(date -u +%Y%m%dT%H%M%SZ); mkdir -p "$OUT"   # 외부 저장소
exec 2>>"$OUT/collect-errors.log"                                       # 수집 중 오류도 남긴다

# 1) 사라지기 쉬운 것부터: 기본 정보 · 프로세스 · 연결 · 열린 소켓 · 실행 파일 링크
{ date -u; uptime; w; } > "$OUT/basic.txt"
ps auxwwf > "$OUT/ps.txt"
ss -tanup > "$OUT/ss.txt"
lsof -nP -i > "$OUT/lsof-net.txt" 2>/dev/null
ls -l /proc/*/exe 2>/dev/null > "$OUT/proc-exe.txt"          # 삭제된 채 실행 중인 것 (deleted)

# 2) 메모리 전체 (avml 은 성공하면 아무것도 출력하지 않는다)
if command -v avml >/dev/null; then
  avml "$OUT/memory.lime"                                     # 메모리 덤프 (도구는 미리 설치)
else
  echo "avml 없음: 메모리 덤프 생략" >&2
fi

# 3) 로그인 기록 · 저널 · 로그 복사
last -Fai > "$OUT/last.txt"
journalctl --since "30 days ago" -o short-iso > "$OUT/journal.txt"
cp -a /var/log "$OUT/varlog"

# 4) 해시: 나중에 증거가 바뀌지 않았다는 근거 (sha256sum -c SHA256SUMS 로 확인)
cd "$OUT" || exit 1
files=(*.txt)
[ -f memory.lime ] && files+=(memory.lime)
sha256sum -- "${files[@]}" > SHA256SUMS
echo "$OUT"
