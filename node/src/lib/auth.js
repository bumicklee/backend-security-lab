// JWT 발급·검증 (0-2 수업). 서명 검증 + "우리가 발급한 토큰인가"(jti 발급 기록) 확인.
import { generateKeyPairSync, randomUUID } from "node:crypto";
import { readFileSync } from "node:fs";
import jwt from "jsonwebtoken";

const TOKEN_TTL_SEC = 15 * 60;
export const DEFAULT_ISSUER = "https://api.example.com";

// 실습에서는 프로세스가 뜰 때마다 RS256 키 쌍을 새로 만든다.
// 운영이라면 키는 KMS·시크릿 저장소에 두고 공개키만 서버에 배포한다 (5-2·5-3 수업).
export function loadKeys(env = process.env) {
  if (env.JWT_PRIVATE_KEY_FILE && env.JWT_PUBLIC_KEY_FILE) {
    return {
      privateKey: readFileSync(env.JWT_PRIVATE_KEY_FILE, "utf8"),
      publicKey: readFileSync(env.JWT_PUBLIC_KEY_FILE, "utf8"),
    };
  }
  const { privateKey, publicKey } = generateKeyPairSync("rsa", {
    modulusLength: 2048,
    publicKeyEncoding: { type: "spki", format: "pem" },
    privateKeyEncoding: { type: "pkcs8", format: "pem" },
  });
  return { privateKey, publicKey };
}

export function bearer(req) {
  const h = req.get("authorization") ?? "";
  const m = /^Bearer\s+(\S+)$/i.exec(h);
  return m ? m[1] : "";
}

export function createAuth({ keys, issued, issuer = DEFAULT_ISSUER, signToken, events }) {
  const PUB_KEY = keys.publicKey;

  async function issueToken(user) {
    const jti = randomUUID();
    if (signToken) {
      // 5-7 KMS 서명: 키는 KMS 밖으로 나오지 않는다. signToken 이 발급 기록(issued.save)도 남긴다
      const iat = Math.floor(Date.now() / 1000);
      const token = await signToken({
        sub: user.username, role: user.role, iss: issuer, iat, exp: iat + TOKEN_TTL_SEC, jti });
      return { ok: true, user: user.username, role: user.role, token };
    }
    const token = jwt.sign({ sub: user.username, role: user.role }, keys.privateKey, {
      algorithm: "RS256",
      expiresIn: TOKEN_TTL_SEC,
      jwtid: jti,
      issuer, // 3-2: 검증할 때 발급자도 확인한다
    });
    await issued.add(jti, TOKEN_TTL_SEC, user.username); // 발급 기록 (사용자별 색인 포함)
    return { ok: true, user: user.username, role: user.role, token };
  }

  // 수업 화면의 미들웨어와 같은 흐름
  function requireAuth() {
    return async (req, res, next) => {
      try {
        const c = jwt.verify(bearer(req), PUB_KEY, {
          algorithms: ["RS256"],   // alg 고정
          issuer,                  // 발급자 확인 (exp 도 검사)
        });
        // 우리가 발급한 토큰인가? (로그아웃·폐기된 토큰도 여기서 걸린다)
        const ok = await issued.has(c.jti);
        if (!ok) return res.status(401).end();
        req.user = c;
        next();
      } catch {
        return res.status(401).end();
      }
    };
  }

  // 6-2 인가 실패는 보안 이벤트(authz_denied)로 남긴다 → fail2ban(board-security)이 IP 별로 센다
  function requireRole(role) {
    return (req, res, next) => {
      if (req.user?.role === role) return next();
      events?.emit("authz_denied", { user: req.user?.sub ?? null, ip: req.ip, path: req.path });
      res.status(403).end();
    };
  }

  async function revoke(jti) {
    await issued.delete(jti);
  }

  return { issueToken, requireAuth, requireRole, revoke };
}
