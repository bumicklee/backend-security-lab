// 4-2 서식이 필요한 본문은 허용 목록으로 정화한다 (DOMPurify + jsdom)
import createDOMPurify from "dompurify";
import { JSDOM } from "jsdom";

const DOMPurify =
  createDOMPurify(new JSDOM("").window);

export const ALLOWED_TAGS = ["p", "b", "i", "a", "ul", "li"];

// 허용 목록 밖 태그(script·img …)와 on* 속성, javascript: 링크는 지워진다
export function sanitizeRich(html) {
  return DOMPurify.sanitize(
    html, { ALLOWED_TAGS });
}
