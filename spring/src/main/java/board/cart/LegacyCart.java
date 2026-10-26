package board.cart;

import java.io.Serial;
import java.io.Serializable;

/**
 * 예전 앱 버전이 Java 직렬화로 보내던 장바구니 (2-7).
 * 새 코드에서는 쓰지 말고 Cart(JSON) 로 옮긴다. 남겨 둔다면 ObjectInputFilter 허용 목록이 필수.
 */
public class LegacyCart implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    public LegacyCartItem[] items;

    public LegacyCart(LegacyCartItem... items) {
        this.items = items;
    }

    public static class LegacyCartItem implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        public long productId;
        public int qty;

        public LegacyCartItem(long productId, int qty) {
            this.productId = productId;
            this.qty = qty;
        }
    }
}
