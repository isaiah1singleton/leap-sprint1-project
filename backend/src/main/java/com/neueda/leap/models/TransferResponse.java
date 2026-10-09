package com.neueda.leap.models;

public record TransferResponse(CashMovementResponse movement, AccountBalanceResponse balance) {}
