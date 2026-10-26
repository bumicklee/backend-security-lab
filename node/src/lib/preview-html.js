// 링크 미리보기: HTML에서 제목과 대표 이미지를 뽑는다. HTML이 아니면 앞부분 텍스트.
export function extractTitle(html) {
  const m = /<title[^>]*>([^<]*)<\/title>/i.exec(html);
  if (m) return m[1].trim().slice(0, 200);
  return String(html).trim().slice(0, 200) || null;
}

export function extractImage(html) {
  const m = /<meta[^>]+property=["']og:image["'][^>]+content=["']([^"']+)["']/i.exec(html);
  return m ? m[1] : null;
}

export function toPreview(html) {
  return { title: extractTitle(html), image: extractImage(html) };
}
