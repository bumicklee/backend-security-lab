package board.order;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

/** 주문 생성 요청. 허용할 타입·범위·형식을 DTO 한 곳에 선언한다 (2-2). */
public record OrderRequest(
    @NotNull @Positive Long productId,
    @Min(1) @Max(100) int qty,
    @Pattern(regexp = "^[A-Z0-9]{8}$")
    String coupon) {}
