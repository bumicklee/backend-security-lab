package board.payment;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 다형성은 이름 있는 하위 타입만 허용 (2-7).
 * JSON 의 "type" 값이 card·bank 가 아니면 400. 클래스 이름(@class)으로 타입을 고르지 않는다.
 */
@JsonTypeInfo(use = Id.NAME, property = "type")
@JsonSubTypes({
    @Type(value = Payment.Card.class, name = "card"),
    @Type(value = Payment.Bank.class, name = "bank") })
public sealed interface Payment {

    record Card(@NotBlank @Pattern(regexp = "^[0-9]{12,19}$") String cardNo) implements Payment {}

    record Bank(@NotBlank @Pattern(regexp = "^[0-9]{3}$") String bankCode,
                @NotBlank @Pattern(regexp = "^[0-9-]{6,20}$") String accountNo) implements Payment {}
}
