package com.neueda.leap.models;

import com.neueda.leap.entities.CashMovement;
import com.neueda.leap.enums.CashMovementType;

import java.time.OffsetDateTime;
import com.neueda.leap.enums.Currency;


public record CashMovementResponse(
    Integer cashMovementId,
    Integer accountId,
    Integer fillId,
    MoneyResponse amount,
    CashMovementType movementType,
    Currency currency,
    OffsetDateTime occurredAt,
    String reason
) {
    public static CashMovementResponse from(CashMovement movement) {
        Integer fillId = movement.getFill() == null
                ? null
                : movement.getFill().getFillId();

        return new CashMovementResponse(
                movement.getCashMovementId(),
                movement.getAccount().getAccountId(),
                fillId,
                new MoneyResponse(
                        movement.getAmount(),
                        movement.getCurrency()
                ),
                movement.getCashMovementType(),
                movement.getCurrency(),
                movement.getOccurredAt(),
                movement.getReason()
        );
    }

}
