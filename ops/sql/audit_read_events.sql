-- 6-10 범위 산정용 DB 감사 로그: 누가(어느 주소·어느 프로그램으로) 어떤 회원의 어떤 컬럼을 언제 읽었나.
-- psql -d audit -f audit_read_events.sql
-- 읽기(SELECT)는 트리거로 잡을 수 없다. pgaudit(pgaudit.log = 'read') 같은 DB 감사 로그를 수집해
-- 회원 단위·컬럼 단위로 풀어 이 테이블에 넣는다(적재 작업은 환경마다 다르므로 여기서는 표 모양만 정한다).
-- 5-4 privacy_access_log(앱이 남기는 접속기록)와 달리, 앱을 거치지 않은 직접 접속(psql 등)도 남는다.
-- 기록은 쓰기만: 적재 계정(audit_writer)에는 INSERT·SELECT 만 준다 (audit_schema.sql 과 같은 원칙).

CREATE SCHEMA IF NOT EXISTS audit;

CREATE TABLE IF NOT EXISTS audit.read_events (
  id          bigserial   PRIMARY KEY,
  ts          timestamptz NOT NULL,          -- 조회 시각 (UTC 로 저장)
  client_addr inet        NOT NULL,          -- 접속한 주소 (감염 서버 10.0.1.20 등)
  app_name    text        NOT NULL DEFAULT '', -- application_name (정상 앱은 board-api)
  db_user     text        NOT NULL,          -- DB 계정
  table_name  text        NOT NULL,
  column_name text        NOT NULL,          -- name · phone · address …
  user_id     bigint      NOT NULL           -- 조회된 회원(정보주체)
);
CREATE INDEX IF NOT EXISTS read_events_addr_ts_idx ON audit.read_events (client_addr, ts);

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'audit_writer') THEN
    CREATE ROLE audit_writer NOLOGIN;
  END IF;
END $$;
REVOKE ALL ON audit.read_events FROM PUBLIC, audit_writer;
GRANT USAGE ON SCHEMA audit TO audit_writer;
GRANT INSERT, SELECT ON audit.read_events TO audit_writer;
GRANT USAGE ON SEQUENCE audit.read_events_id_seq TO audit_writer;
