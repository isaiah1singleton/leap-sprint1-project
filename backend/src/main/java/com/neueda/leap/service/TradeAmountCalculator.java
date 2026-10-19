package com.neueda.leap.service;

import com.neueda.leap.entities.Money;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Calculates the positive settlement amount for a full-fill trade.
 *
 * Uses the execution price's currency without conversion.
 * The caller determines whether cash is debited or credited.
 */
@Component
public class TradeAmountCalculator {

    private static final int SETTLEMENT_SCALE = 8;
    private static final int MAX_INTEGER_DIGITS = 16;

    private static final RoundingMode ROUNDING_MODE =
            RoundingMode.HALF_EVEN;

    private static final BigDecimal MAX_AMOUNT =
            new BigDecimal("9999999999999999.99999999");

    public Money calculate(
            BigDecimal quantity,
            Money executionPrice
    ) {
        validateQuantity(quantity);

        if (executionPrice == null
                || executionPrice.amount().signum() <= 0) {
            throw new IllegalArgumentException(
                    "Execution price must be positive."
            );
        }

        BigDecimal settlementAmount = quantity
                .multiply(executionPrice.amount())
                .setScale(SETTLEMENT_SCALE, ROUNDING_MODE);

        if (settlementAmount.signum() == 0) {
            throw new IllegalArgumentException(
                    "Trade amount is below the supported "
                            + "settlement precision."
            );
        }

        if (settlementAmount.compareTo(MAX_AMOUNT) > 0) {
            throw new IllegalArgumentException(
                    "Trade amount exceeds the supported balance range."
            );
        }

        return new Money(
                settlementAmount,
                executionPrice.currency()
        );
    }

    private static void validateQuantity(BigDecimal quantity) {
        if (quantity == null || quantity.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Trade quantity must be positive."
            );
        }

        BigDecimal normalized = quantity.stripTrailingZeros();

        int fractionalDigits = Math.max(
                0,
                normalized.scale()
        );

        int integerDigits = Math.max(
                0,
                normalized.precision() - normalized.scale()
        );

        if (fractionalDigits > SETTLEMENT_SCALE
                || integerDigits > MAX_INTEGER_DIGITS) {
            throw new IllegalArgumentException(
                    "Trade quantity must fit NUMERIC(24, 8)."
            );
        }
    }
}
