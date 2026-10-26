-- 5-7 매일 07:00 (analyst@audit): 발급 기록에 없는 jti 로 들어온 호출 — 위조 토큰 의심
-- access_log: API 관문이 남긴 요청 기록(jti · ip · at), token_issued: 발급 서버가 남긴 발급 기록(audit_schema.sql)
SELECT a.jti, count(*) AS calls, count(DISTINCT a.ip) AS ips
  FROM access_log a
  LEFT JOIN token_issued t ON t.jti = a.jti
 WHERE t.jti IS NULL
   AND a.at > now() - interval '1 day'
 GROUP BY a.jti
 ORDER BY calls DESC
 LIMIT 5;
