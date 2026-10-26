// 3-6 패스키(WebAuthn) — @simplewebauthn/server 14.
// 서명은 오리진·RP ID 에 묶여 있어, 피싱 사이트(다른 오리진)에서 받은 응답은 검증을 통과하지 못한다.
// 챌린지는 세션에 두고 한 번만 쓴다. 검증 함수는 deps.webauthn 으로 바꿔 끼울 수 있다(테스트).
import { Router } from "express";
import * as simplewebauthn from "@simplewebauthn/server";

// 세션 재발급 후 로그인 상태 기록 (세션 고정 방어, 3-2)
export async function startSession(req, userId) {
  await new Promise((ok, fail) => req.session.regenerate((e) => (e ? fail(e) : ok())));
  req.session.user = userId;
  await new Promise((ok, fail) => req.session.save((e) => (e ? fail(e) : ok())));
}

export function passkeyRoutes({ config, stores, webauthn }, auth) {
  const router = Router();
  const { passkeys } = stores;
  const {
    generateRegistrationOptions, verifyRegistrationResponse,
    generateAuthenticationOptions, verifyAuthenticationResponse,
  } = webauthn ?? simplewebauthn;
  const expectedOrigin = config.webauthnOrigin;   // 화면: "https://board.example.com"
  const expectedRPID = config.webauthnRpId;       // 화면: "board.example.com"

  // 등록: 이미 로그인한 사용자(JWT)가 패스키를 추가한다
  router.post("/webauthn/register/options", auth.requireAuth(), async (req, res) => {
    const existing = await passkeys.listFor(req.user.sub);
    const options = await generateRegistrationOptions({
      rpName: "Board", rpID: expectedRPID, userName: req.user.sub,
      attestationType: "none",
      excludeCredentials: existing.map((c) => ({ id: c.id, transports: c.transports })),
      authenticatorSelection: { residentKey: "preferred", userVerification: "preferred" },
    });
    req.session.webauthnRegistration = { challenge: options.challenge, userId: req.user.sub };
    res.json(options);
  });

  router.post("/webauthn/register/finish", auth.requireAuth(), async (req, res) => {
    const saved = req.session.webauthnRegistration;
    delete req.session.webauthnRegistration;                     // 챌린지는 한 번만
    if (!saved || saved.userId !== req.user.sub) return res.sendStatus(400);
    const { verified, registrationInfo } = await verifyRegistrationResponse({
      response: req.body, expectedChallenge: saved.challenge,
      expectedOrigin, expectedRPID,
    }).catch(() => ({ verified: false }));
    if (!verified) return res.sendStatus(400);
    const { credential } = registrationInfo;
    await passkeys.add({
      id: credential.id, publicKey: credential.publicKey, counter: credential.counter,
      transports: credential.transports, userId: req.user.sub,
    });
    res.sendStatus(201);
  });

  // 로그인: 챌린지 발급 → 서명 검증
  router.post("/webauthn/login/options", async (req, res) => {
    const options = await generateAuthenticationOptions({ rpID: expectedRPID, userVerification: "preferred" });
    req.session.webauthnChallenge = options.challenge;
    res.json(options);
  });

  router.post("/webauthn/login/finish", async (req, res) => {
    const challenge = req.session.webauthnChallenge;
    delete req.session.webauthnChallenge;                        // 챌린지는 한 번만
    const cred = await passkeys.findById(req.body?.id);
    if (!challenge || !cred) return res.sendStatus(401);
    const { verified, authenticationInfo } = await verifyAuthenticationResponse({
      response: req.body, expectedChallenge: challenge, credential: cred,
      expectedOrigin, expectedRPID,
    }).catch(() => ({ verified: false }));
    if (!verified) return res.sendStatus(401);                   // 다른 오리진의 서명이면 여기서 끝
    await passkeys.updateCounter(cred.id, authenticationInfo.newCounter);
    await startSession(req, cred.userId);                        // 세션 재발급 (3-2)
    res.sendStatus(204);
  });

  return router;
}
