package com.neueda.leap.entities;

import com.neueda.leap.enums.Currency;
import java.math.BigDecimal;
import java.util.Objects;

public record Money(BigDecimal amount, Currency currency) {
    public Money {
        Objects.requireNonNull(amount, "Amount is required.");
        Objects.requireNonNull(currency, "Currency is required.");
    }

    public Money add(Money other) {
        requireSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    public Money subtract(Money other) {
        requireSameCurrency(other);
        return new Money(amount.subtract(other.amount), currency);
    }

    public Money multiply(BigDecimal factor) {
        return new Money(amount.multiply(Objects.requireNonNull(factor, "Factor is required.")), currency);
    }

    private void requireSameCurrency(Money other) {
        if (other == null || currency != other.currency) {
            throw new IllegalArgumentException("Money values must have the same currency.");
        }
    }
}
