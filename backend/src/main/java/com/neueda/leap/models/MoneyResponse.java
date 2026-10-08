package com.neueda.leap.models;

import java.math.BigDecimal;
import com.neueda.leap.enums.Currency;

public record MoneyResponse(
    BigDecimal amount,
    Currency currency
) {}
