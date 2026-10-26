package board.preview;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 링크 미리보기 결과. HTML 에서 title 과 og:image 만 뽑는다. */
public record Preview(String title, String image) {
    private static final Pattern TITLE = Pattern.compile("<title[^>]*>(.*?)</title>",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern OG_IMAGE = Pattern.compile(
        "<meta[^>]+property=[\"']og:image[\"'][^>]+content=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);

    public static Preview from(String html) {
        if (html == null) return new Preview(null, null);
        return new Preview(first(TITLE.matcher(html)), first(OG_IMAGE.matcher(html)));
    }

    private static String first(Matcher m) {
        if (!m.find()) return null;
        String v = m.group(1).trim();
        return v.length() > 300 ? v.substring(0, 300) : v;
    }
}
