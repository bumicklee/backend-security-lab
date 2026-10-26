package board.cart;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 외부 입력은 JSON + 구체 DTO 로 받는다 (2-7 권장 방식). */
public record Cart(
    @NotEmpty @Size(max = 50) List<@Valid @NotNull Item> items) {

    public record Item(
        @NotNull @Positive Long productId,
        @Min(1) @Max(100) int qty) {}
}
