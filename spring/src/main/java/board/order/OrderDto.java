package board.order;

public record OrderDto(long id, String user, long productId, int qty, long total, String coupon) {
    public static OrderDto from(Order o) {
        return new OrderDto(o.getId(), o.getOwner(), o.getProductId(), o.getQty(), o.getTotal(), o.getCoupon());
    }
}
