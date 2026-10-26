// 3-3 OAuth 2.0 · OIDC 로그인 (openid-client 6). PKCE(S256) · state · nonce 를 이 브라우저 세션에 두고 한 번만 쓴다.
// IdP 설정(OIDC_ISSUER)이 없으면 503. 테스트는 deps.oidcDiscovery 로 가짜 IdP 설정을 주입한다.
import { Router } from "express";
import * as client from "openid-client";

export function oidcRoutes({ config, users, oidcDiscovery }) {
  const router = Router();
  const REDIRECT_URI = config.oidcRedirectUri;                 // 등록한 값과 글자 그대로
  const discover = oidcDiscovery ?? (() =>
    client.discovery(new URL(config.oidcIssuer), config.oidcClientId, config.oidcClientSecret));
  let configPromise;
  const getConfig = () => {
    if (!oidcDiscovery && !config.oidcIssuer) return null;
    configPromise ??= discover().catch((e) => {
      configPromise = undefined; // 다음 요청에서 다시 시도
      throw e;
    });
    return configPromise;
  };

  // 이메일이 아니라 iss + sub 로 찾는다. 처음 보는 신원이면 새 계정 (기존 계정에 자동 연결하지 않는다)
  async function findByIdentity(iss, sub) {
    const found = await users.findOne({ oidcIss: iss, oidcSub: sub });
    if (found) return found;
    const user = { username: `oidc:${sub}`, role: "USER", oidcIss: iss, oidcSub: sub };
    await users.insertOne(user);
    return user;
  }

  async function start(req, res) {
    const pending = getConfig();
    if (!pending) return res.status(503).json({ error: "oidc not configured" });
    const oidc = await pending;
    const verifier = client.randomPKCECodeVerifier();
    const state = client.randomState(), nonce = client.randomNonce();
    req.session.oauth = { verifier, state, nonce };            // 이 브라우저 세션에만
    res.redirect(client.buildAuthorizationUrl(oidc, {
      redirect_uri: REDIRECT_URI, scope: "openid profile email", state, nonce,
      code_challenge: await client.calculatePKCECodeChallenge(verifier),
      code_challenge_method: "S256" }).href);
  }
  router.get("/login/idp", start);
  router.get("/login/oidc", start);

  router.get("/callback", async (req, res) => {
    const saved = req.session?.oauth;
    if (req.session) delete req.session.oauth;                 // 한 번만 쓴다
    if (!saved) return res.sendStatus(400);
    const pending = getConfig();
    if (!pending) return res.status(503).json({ error: "oidc not configured" });
    const oidc = await pending;
    let tokens;
    try {
      tokens = await client.authorizationCodeGrant(oidc,
        new URL(req.originalUrl, new URL(REDIRECT_URI).origin), {
          pkceCodeVerifier: saved.verifier,
          expectedState: saved.state, expectedNonce: saved.nonce });
    } catch {
      return res.sendStatus(401);                               // state·nonce·서명·aud 불일치, 코드 교환 실패
    }
    const { iss, sub } = tokens.claims();                       // iss·aud·exp·nonce 검증을 통과한 값
    await new Promise((ok, fail) => req.session.regenerate((e) => (e ? fail(e) : ok())));
    req.session.user = (await findByIdentity(iss, sub)).username;   // 이메일이 아니라 iss + sub
    res.redirect("/");
  });

  return router;
}
