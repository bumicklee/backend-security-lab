-- 감사용 DB(audit) 예시 스키마. psql -d audit -f audit_schema.sql
-- 5-7 find_unissued_tokens.sql 이 읽는 두 테이블 + 5-4 개인정보 접속기록.
-- 기록은 쓰기만: 앱 계정(audit_writer)에는 INSERT·SELECT 만 주고 UPDATE·DELETE·TRUNCATE 는 주지 않는다.

CREATE TABLE IF NOT EXISTS token_issued (
  jti        text PRIMARY KEY,
  sub        text        NOT NULL,
  issued_at  timestamptz NOT NULL DEFAULT now(),
  expires_at timestamptz NOT NULL
);

CREATE TABLE IF NOT EXISTS access_log (
  id     bigserial PRIMARY KEY,
  at     timestamptz NOT NULL DEFAULT now(),
  jti    text,
  sub    text,
  ip     inet,
  method text NOT NULL,
  path   text NOT NULL,
  status int  NOT NULL
);
CREATE INDEX IF NOT EXISTS access_log_at_idx ON access_log (at);

-- 5-4 접속기록 다섯 항목: 식별자 · 접속일시 · 접속지 · 처리한 정보주체 · 수행업무 (+ 결과·건수)
-- 1년 이상(5만 명 이상·고유식별정보·민감정보 처리 시스템은 2년 이상) 보관
CREATE TABLE IF NOT EXISTS privacy_access_log (
  id       bigserial PRIMARY KEY,
  actor_id text        NOT NULL,
  at       timestamptz NOT NULL,
  from_ip  text        NOT NULL,
  subjects text,
  task     text        NOT NULL,
  status   int         NOT NULL,
  count    int         NOT NULL
);

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'audit_writer') THEN
    CREATE ROLE audit_writer NOLOGIN;
  END IF;
END $$;
REVOKE ALL ON token_issued, access_log, privacy_access_log FROM PUBLIC, audit_writer;
GRANT INSERT, SELECT ON token_issued, access_log, privacy_access_log TO audit_writer;
GRANT USAGE ON SEQUENCE access_log_id_seq, privacy_access_log_id_seq TO audit_writer;
