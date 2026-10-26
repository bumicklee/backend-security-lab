// 4-5 CSRF: csurf 대신 출처 확인 (Express 5)
// - Sec-Fetch-Site: same-origin(같은 출처) · none(주소창 직접 입력) 이면 통과
// - 그 밖에는 Origin 이 허용 목록에 있어야 통과
// - 두 헤더가 모두 없으면 브라우저가 아닌 클라이언트(curl·모바일 앱·서버 간 호출)로 보고 통과.
//   이런 요청에는 브라우저가 쿠키를 자동으로 붙이는 일이 없어 CSRF 대상이 아니다.
//   (브라우저는 다른 출처로 보내는 POST 에 Origin 을 반드시 붙인다)
export function originCheck(allowed) {
  return (req, res, next) => {
    if (["GET", "HEAD", "OPTIONS"].includes(req.method))
      return next();
    const site = req.get("Sec-Fetch-Site");
    if (site === "same-origin" || site === "none") return next();
    const origin = req.get("Origin");
    if (allowed.includes(origin))
      return next();
    if (site === undefined && origin === undefined) return next();
    res.sendStatus(403);
  };
}
