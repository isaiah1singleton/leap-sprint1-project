package com.neueda.leap.models;

import com.neueda.leap.enums.CashMovementType;
import java.math.BigDecimal;

public record TransferRequest(CashMovementType type, BigDecimal amount) {}
