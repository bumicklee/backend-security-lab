// 5-1 내부 API 클라이언트: 인증서 검증을 끄지 않고, 이 클라이언트에만 사내 CA 를 지정한다.
//   ✗ new https.Agent({ rejectUnauthorized: false }) · NODE_TLS_REJECT_UNAUTHORIZED=0
//   ✓ new https.Agent({ ca: fs.readFileSync(INTERNAL_CA) })
// ca 를 주면 이 에이전트는 기본 신뢰 목록 대신 사내 CA 만 믿는다(공개 CA 가 발급한 인증서도 거절).
// 기본 목록에 사내 CA 를 "더하려면" 프로세스 전체에 NODE_EXTRA_CA_CERTS=/path/ca.pem
// (또는 ca: [...tls.rootCertificates, internalCa]).
import fs from "node:fs";
import https from "node:https";

export function createInternalAgent(caPem) {
  if (!caPem || !String(caPem).includes("BEGIN CERTIFICATE")) {
    throw new Error("사내 CA 인증서(PEM)가 필요합니다");
  }
  return new https.Agent({
    ca: caPem, keepAlive: true });
}

export function createInternalApi({ baseUrl, caFile, readFile = fs.readFileSync, timeoutMs = 5000 }) {
  const base = new URL(baseUrl);
  if (base.protocol !== "https:") throw new Error("내부 API 도 https 로만 호출합니다");
  // 이 클라이언트는 사내 CA 만 신뢰
  const agent = createInternalAgent(readFile(caFile));

  function getJson(pathname) {
    const url = new URL(pathname, base);
    if (url.origin !== base.origin) throw new Error("내부 API 주소 밖으로는 보내지 않습니다");
    return new Promise((resolve, reject) => {
      const req = https.get(url, { agent, timeout: timeoutMs }, (res) => {
        let body = "";
        res.setEncoding("utf8");
        res.on("data", (c) => (body += c));
        res.on("end", () => {
          if (res.statusCode >= 400) return reject(new Error(`internal api ${res.statusCode}`));
          try { resolve(JSON.parse(body)); } catch (e) { reject(e); }
        });
      });
      req.on("timeout", () => req.destroy(new Error("internal api timeout")));
      req.on("error", reject); // 인증서가 사내 CA 로 검증되지 않으면 여기로 (핸드셰이크에서 끊긴다)
    });
  }

  return { agent, getJson };
}
