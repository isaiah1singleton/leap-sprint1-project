package com.neueda.leap.models;

import com.neueda.leap.enums.OrderSide;
import java.math.BigDecimal;

public record SubmitOrderRequest(Integer accountId, Integer instrumentId, OrderSide side, BigDecimal quantity) {}
