package com.gig0d.banking.shared;

import org.springframework.http.HttpStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;

public final class Money {
    public static final BigDecimal MAX_MOVEMENT = new BigDecimal("1000000.00");
    private Money() {}
    public static BigDecimal amount(BigDecimal input) {
        if (input == null || input.signum() <= 0 || input.compareTo(MAX_MOVEMENT) > 0 || input.scale() > 2) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_AMOUNT", "Use a positive USD amount with at most two decimals, up to 1000000.00.");
        }
        return input.setScale(2, RoundingMode.UNNECESSARY);
    }
}
