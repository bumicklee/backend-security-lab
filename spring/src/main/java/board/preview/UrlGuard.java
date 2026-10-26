package board.preview;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * SSRF 방어 검사 (2-6).
 * 1) 스킴·호스트 허용 목록  2) 해석된 모든 IP 가 공인 주소인지
 */
public final class UrlGuard {
    private UrlGuard() {}

    public static URI check(String url, Set<String> allowedHosts) {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            throw badRequest();
        }
        String host = uri.getHost();
        if (!"https".equals(uri.getScheme())
            || host == null
            || uri.getUserInfo() != null
            || (uri.getPort() != -1 && uri.getPort() != 443)
            || !allowedHosts.contains(host.toLowerCase(Locale.ROOT)))
          throw badRequest();          // 허용 목록

        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);   // 해석된 IP
        } catch (UnknownHostException e) {
            throw badRequest();
        }
        for (var a : addresses)
          if (!isPublic(a))
            throw badRequest();
        return uri;
    }

    /** 루프백·사설·링크로컬(169.254.x.x 메타데이터)·CGNAT·ULA·멀티캐스트는 모두 거절 */
    public static boolean isPublic(InetAddress a) {
        if (a.isAnyLocalAddress() || a.isLoopbackAddress() || a.isSiteLocalAddress()
            || a.isLinkLocalAddress() || a.isMulticastAddress()) {
            return false;
        }
        byte[] b = a.getAddress();
        if (a instanceof Inet4Address) {
            int first = b[0] & 0xff, second = b[1] & 0xff;
            if (first == 0) return false;                                 // 0.0.0.0/8
            if (first == 100 && second >= 64 && second <= 127) return false; // 100.64.0.0/10
            if (first == 192 && second == 0 && (b[2] & 0xff) == 0) return false; // 192.0.0.0/24
        }
        if (a instanceof Inet6Address) {
            if ((b[0] & 0xfe) == 0xfc) return false;                     // fc00::/7 (ULA)
        }
        return true;
    }

    private static ResponseStatusException badRequest() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "url not allowed");
    }
}
