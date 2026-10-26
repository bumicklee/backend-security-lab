// 아주 작은 JSON 로거. 요청 본문은 남기지 않고, 필요한 필드만 남긴다.
const LEVELS = { debug: 10, info: 20, warn: 30, error: 40, silent: 100 };

export function createLogger(level = "info") {
  const min = LEVELS[level] ?? LEVELS.info;
  const write = (lvl, msg, fields = {}) => {
    if (LEVELS[lvl] < min) return;
    const line = JSON.stringify({ time: new Date().toISOString(), level: lvl, msg, ...fields });
    (lvl === "error" ? console.error : console.log)(line);
  };
  return {
    debug: (m, f) => write("debug", m, f),
    info: (m, f) => write("info", m, f),
    warn: (m, f) => write("warn", m, f),
    error: (m, f) => write("error", m, f),
  };
}

// 접속 기록: 누가(userId) 무엇을(method, path) 했는지. 쿼리스트링·본문은 남기지 않는다.
export function accessLog(logger) {
  return (req, res, next) => {
    const start = process.hrtime.bigint();
    res.on("finish", () => {
      logger.info("access", {
        method: req.method,
        path: req.path,
        status: res.statusCode,
        userId: req.user?.sub ?? null,
        ip: req.ip,
        ms: Number((process.hrtime.bigint() - start) / 1_000_000n),
      });
    });
    next();
  };
}
