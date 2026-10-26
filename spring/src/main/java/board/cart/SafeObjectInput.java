package board.cart;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;

/**
 * ObjectInputFilter 허용 목록을 건 역직렬화 (2-7).
 * 목록에 없는 클래스가 스트림에 나오면 객체를 만들기 전에 InvalidClassException 으로 멈춘다.
 */
public final class SafeObjectInput {
    private SafeObjectInput() {}

    // 허용할 클래스만 나열하고 마지막 !* 로 나머지 전부 거절. 깊이·개수·크기 제한도 함께.
    static final ObjectInputFilter CART_FILTER = ObjectInputFilter.Config.createFilter(
        "maxdepth=5;maxrefs=1000;maxarray=100;maxbytes=65536;"
            + "board.cart.LegacyCart;board.cart.LegacyCart$LegacyCartItem;!*");

    public static LegacyCart readCart(InputStream body) throws IOException, ClassNotFoundException {
        try (var in = new ObjectInputStream(body)) {
            in.setObjectInputFilter(CART_FILTER);
            return (LegacyCart) in.readObject();
        }
    }
}
