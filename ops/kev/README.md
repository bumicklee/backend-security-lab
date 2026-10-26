# 7-3 KEV 자동 대조 스크립트

수업 7-3 화면의 스크립트입니다. 호스트를 Trivy로 스캔하고, CISA KEV와 FIRST EPSS에 대조해 P1~P4 등급을 매깁니다. P1(KEV 등재)이 있거나 스캔·대조가 실패하면 메신저로 알립니다.

| 파일 | 설치 위치 | 하는 일 |
| --- | --- | --- |
| `kev_watch.py` | `/opt/kev_watch.py` | Trivy JSON → KEV·EPSS 대조, 등급 출력. P1이 있으면 종료 코드 2 |
| `kev_daily.sh` | `/opt/kev_daily.sh` | 스캔 → 대조 → 종료 코드가 0이 아니면 `notify.sh` 호출 |
| `notify.sh` | `/opt/notify.sh` | 로그를 웹훅으로 전송(Slack 호환 JSON) |
| `kev-watch.cron` | `/etc/cron.d/kev-watch` | 매일 08:30(서버 시간대 기준) |
| `mock/` | — | 판정 규칙 시험용 모의 KEV·EPSS·스캔 결과 |

## 준비

- Python 3.9 이상, Trivy(버전 고정 + 서명 확인 후 설치, 6-4), curl.
- `kev_daily.sh`는 맨 위에서 `PATH=/usr/local/bin:/usr/bin:/bin`을 지정합니다. cron의 기본 PATH에는 `/usr/local/bin`이 없어서 공식 설치 스크립트로 넣은 trivy를 못 찾기 때문입니다.
- 웹훅 주소는 코드에 쓰지 않습니다. 비밀 저장소에서 받아 root만 읽는 파일에 둡니다(5-3).

```bash
sudo install -m 755 kev_watch.py kev_daily.sh notify.sh /opt/
sudo install -m 644 kev-watch.cron /etc/cron.d/kev-watch   # cron.d 파일 이름에는 점(.)을 쓰지 않는다
sudo install -d -m 700 /etc/kev_watch /var/lib/kev_watch
sudo sh -c 'umask 077; printf "%s\n" "https://hooks.example.com/…" > /etc/kev_watch/webhook_url'
timedatectl | grep "Time zone"     # 08:30은 서버 시간대 기준. 클라우드 기본은 UTC
```

## 모의 파일로 판정 확인

판정 규칙을 고쳤다면 실제 피드보다 모의 파일로 먼저 확인합니다.

```bash
python3 kev_watch.py mock/host.json --kev-file mock/kev.json --epss-file mock/epss.json --state /tmp/kev-state.json
echo "exit=$?"     # 2 (P1 있음). 다시 실행하면 'NEW KEV' 표시가 사라진다
```

예상 출력:

```text
P1  CVE-2026-XXXX8  0.871  HIGH      sudo         → 1.9.15p5-3ubuntu5.2  NEW KEV
    KEV 2026-10-26 · CISA 기한 2026-10-29 · 랜섬웨어 Unknown
P2  CVE-2026-XXXX5  0.214  CRITICAL  libexpat1    → 2.6.1-2ubuntu0.3
P3  CVE-2026-XXXX6  0.004  HIGH      zlib1g       → 1:1.3.dfsg-3.1ubuntu2.2
요약: P1 1건, P2 1건, P3 1건, P4 7건
```

CVE 번호와 패키지 버전은 가짜 값입니다. 실제 실행은 `sudo /opt/kev_daily.sh; echo "exit=$?"`.
