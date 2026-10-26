// 테스트·수업 화면용 기본 앱: import { app } from "../src/app.js"
// NODE_ENV=test 이면 DB 없이 메모리 대역으로 뜬다. 취약 라우트(/lab)는 등록하지 않는다.
import { loadConfig } from "./config.js";
import { createApp } from "./create-app.js";
import { createDeps } from "./data/index.js";

export const deps = await createDeps(loadConfig());
export const app = createApp(deps, { labMode: false });
