# 5-6 랜섬웨어도 못 지우는 백업 — restic 스크립트

수업 화면의 명령을 그대로 묶은 셸 스크립트입니다. 서버(리눅스, GNU coreutils)에서 실행하며, 예제 서비스 컨테이너와는 따로 동작합니다.

| 파일 | 어디서 · 언제 | 하는 일 |
| --- | --- | --- |
| `backup.sh` | 앱 서버(web1) · 매일 03:00 | `restic backup /etc /var/lib/app --exclude-caches` + `restic backup --stdin-from-command --stdin-filename board.dump -- pg_dump -Fc board` |
| `check.sh` | 관리 서버(ops) · 매주 일요일 | `restic check --read-data-subset=5%` + 마지막 DB 덤프 스냅숏이 26시간보다 오래되면 실패(알림) |
| `restore-drill.sh` | 복구 훈련 서버(restore-test) · 분기마다 | 최신 스냅숏 복원(`restic restore`) + `restic dump … /board.dump \| pg_restore -d board_restore_test` + `select count(*) from posts` + 걸린 시간 출력 |
| `prune.sh` | 관리 서버(ops) · 매주 | `restic forget --prune --keep-daily 7 --keep-weekly 4 --keep-monthly 6` (삭제 권한이 있는 별도 자격 증명으로) |
| `iam-backup-writer.json` | 앱 서버 역할 정책 | 쓰기·읽기만 허용, 버전 삭제·객체 잠금·수명 주기·버전 관리 설정 변경은 거부 |

## 준비

- restic **0.17 이상**(`--stdin-from-command`), PostgreSQL 클라이언트(`pg_dump`·`pg_restore`·`psql`·`createdb`·`dropdb`, 서버와 같은 주 버전 이상), `jq`.
- **Ubuntu 24.04 의 `apt install restic` 은 0.16.4 라서 `backup.sh` 가 `unknown flag: --stdin-from-command` 로 실패합니다.** 공식 릴리스 바이너리를 체크섬과 함께 설치하세요(arm64 서버는 `linux_arm64`).

  ```bash
  V=0.19.1    # https://github.com/restic/restic/releases 의 최신 버전
  curl -fsSLO https://github.com/restic/restic/releases/download/v$V/restic_${V}_linux_amd64.bz2
  curl -fsSLO https://github.com/restic/restic/releases/download/v$V/SHA256SUMS
  grep "restic_${V}_linux_amd64.bz2" SHA256SUMS | sha256sum -c -    # OK 가 나와야 함
  bunzip2 restic_${V}_linux_amd64.bz2
  sudo install -m 755 restic_${V}_linux_amd64 /usr/local/bin/restic
  restic version                                                     # restic 0.19.1 …
  ```

  apt 로 0.16 을 이미 설치했다면 `sudo apt remove restic` 후 위처럼 설치합니다(`/usr/bin/restic` 이 먼저 잡히지 않게).
- 저장소 버킷은 **객체 잠금(Object Lock) COMPLIANCE 30일** + 버전 관리를 켭니다. 앱 서버 자격 증명으로는 잠긴 버전을 지울 수 없습니다.
- 환경 변수 파일(예: `/etc/restic/env`, 권한 600):

```bash
RESTIC_REPOSITORY=s3:https://s3.example.com/board-backup
RESTIC_PASSWORD_FILE=/etc/restic/password   # 권한 600, 시크릿 매니저에서 받아 둔다 (5-3)
PGHOST=127.0.0.1
PGUSER=backup                               # 비밀번호는 ~/.pgpass (권한 600)
BACKUP_DB=board
BACKUP_HOST=web1                            # 스냅숏 호스트 이름. backup.sh·check.sh·restore-drill.sh 가 같은 값을 쓴다
```

처음 한 번 `restic init` 으로 저장소를 만듭니다. 저장소는 처음부터 암호화되어, 파일이 새어도 비밀번호 없이는 읽을 수 없습니다.

## 예약 (cron 예시)

```cron
# web1
0 3 * * *   root  set -a; . /etc/restic/env; set +a; /opt/board/ops/backup/backup.sh   >> /var/log/restic-backup.log 2>&1
# ops (점검·정리는 앱 서버가 아니라 관리 서버에서)
0 5 * * 0   root  set -a; . /etc/restic/env; set +a; /opt/board/ops/backup/check.sh    || /usr/local/bin/notify "backup check failed"
30 5 * * 0  root  set -a; . /etc/restic/env.admin; set +a; /opt/board/ops/backup/prune.sh
```

## 주의

- DB 덤프는 restic 이 `pg_dump` 를 직접 실행합니다. 덤프가 실패하면 스냅숏이 만들어지지 않고 스크립트도 실패로 끝납니다(`pg_dump … | restic backup --stdin` 파이프는 잘린 덤프가 저장될 수 있어 쓰지 않습니다).
- `restore-drill.sh` 는 이름에 `restore_test` 가 들어간 DB 에만 복원합니다(운영 DB 덮어쓰기 방지). 훈련 결과(시간·건수)는 훈련 기록에 남기고, 최근 3개월 안에 한 번 이상 실행합니다.
- `prune.sh` 는 객체 잠금 기간 안의 데이터는 지우지 못합니다. 앱 서버에서 돌리지 않습니다.
- 셸 문법 검사: `docker run --rm -v "$PWD:/mnt:ro" -w /mnt koalaman/shellcheck:v0.10.0 *.sh`

## 수업 화면과 다른 점

- 화면의 명령에 태그(`--tag files`·`--tag db`)와 환경 변수(`BACKUP_PATHS`·`BACKUP_DB`·`BACKUP_HOST`)가 더해졌습니다. 기본값은 화면과 같은 `/etc /var/lib/app`·`board`·`web1` 입니다.
- `backup.sh` 는 화면의 `restic backup` 에 `--host "${BACKUP_HOST}"`(기본 `web1`)를 더했습니다. 화면은 hostname 이 `web1` 인 서버라 생략했지만, 실습 VM 처럼 hostname 이 다르면 `check.sh`·`restore-drill.sh` 의 `--host web1` 이 스냅숏을 찾지 못하기 때문입니다.
- 화면에 없는 `restic forget --prune` 은 관리 서버용 `prune.sh` 에 따로 두었습니다.
- 복구 훈련은 화면의 `restic restore 7c1d9e42` 처럼 ID 를 적는 대신 최신 스냅숏 ID 를 `restic snapshots --json | jq` 로 고르고, `pg_restore --no-owner` 로 복원합니다.
- `iam-backup-writer.json` 은 화면의 거부 목록에 `s3:BypassGovernanceRetention`·`s3:PutBucketVersioning` 을 더했습니다.
