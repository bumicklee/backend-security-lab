// SSRF 방어용: 해석된 IP가 공인 주소인지 확인한다 (2-6 수업).
import net from "node:net";

const blocked = new net.BlockList();
// IPv4: 현재 망, 사설, CGNAT, 루프백, 링크 로컬(169.254 = 메타데이터), 멀티캐스트, 예약
for (const [addr, prefix] of [
  ["0.0.0.0", 8],
  ["10.0.0.0", 8],
  ["100.64.0.0", 10],
  ["127.0.0.0", 8],
  ["169.254.0.0", 16],
  ["172.16.0.0", 12],
  ["192.0.0.0", 24],
  ["192.168.0.0", 16],
  ["198.18.0.0", 15],
  ["224.0.0.0", 4],
  ["240.0.0.0", 4],
]) {
  blocked.addSubnet(addr, prefix, "ipv4");
}
// IPv6: 미지정, 루프백, ULA, 링크 로컬, 멀티캐스트
for (const [addr, prefix] of [
  ["::", 128],
  ["::1", 128],
  ["fc00::", 7],
  ["fe80::", 10],
  ["ff00::", 8],
]) {
  blocked.addSubnet(addr, prefix, "ipv6");
}

// ::ffff:169.254.169.254, ::ffff:a9fe:a9fe 같은 IPv4 매핑 주소를 IPv4로 바꾼다
function unmapIPv4(addr) {
  const dotted = /^::ffff:(\d+\.\d+\.\d+\.\d+)$/i.exec(addr);
  if (dotted) return dotted[1];
  const hex = /^::ffff:([0-9a-f]{1,4}):([0-9a-f]{1,4})$/i.exec(addr);
  if (hex) {
    const hi = parseInt(hex[1], 16);
    const lo = parseInt(hex[2], 16);
    return [hi >> 8, hi & 255, lo >> 8, lo & 255].join(".");
  }
  return null;
}

export function isPublic(address) {
  const v4 = unmapIPv4(address);
  if (v4) return isPublic(v4);
  const family = net.isIP(address);
  if (family === 4) return !blocked.check(address, "ipv4");
  if (family === 6) return !blocked.check(address, "ipv6");
  return false; // IP가 아니면 공인 주소로 보지 않는다
}
