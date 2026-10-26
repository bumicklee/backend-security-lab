-- 6-10 범위 산정: 감염 서버 주소로 들어와, 정상 앱이 아닌 도구로, 첫 침입 시각 이후 조회한 기록
-- psql -At "$AUDIT_DB" -v addr=10.0.1.20 -v since='2026-10-18 03:12Z' -f breach_scope.sql
-- 첫 침입 시각은 6-9 타임라인(UTC)에서 가져온다.

-- 1) 조회된 이용자 수 (통지 대상)
SELECT count(DISTINCT user_id) FROM audit.read_events
 WHERE client_addr = :'addr' AND app_name <> 'board-api'
   AND ts >= :'since';

-- 2) 조회된 항목 (통지문 "유출된 항목")
SELECT DISTINCT column_name FROM audit.read_events
 WHERE client_addr = :'addr' AND app_name <> 'board-api'
   AND ts >= :'since';

-- 3) 통지 대상 목록 (개인정보 보호책임자·법무 검토용으로 내보낸다)
-- \copy (SELECT DISTINCT user_id FROM audit.read_events WHERE client_addr = '10.0.1.20' AND app_name <> 'board-api' AND ts >= '2026-10-18 03:12Z' ORDER BY 1) TO 'notify-targets.csv' CSV
