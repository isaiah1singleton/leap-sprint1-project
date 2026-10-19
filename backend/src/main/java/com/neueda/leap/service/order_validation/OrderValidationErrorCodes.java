package com.neueda.leap.service.order_validation;

public final class OrderValidationErrorCodes {
    public static final String ORDER_MISSING = "ORDER_MISSING";
    public static final String ACCOUNT_INACTIVE = "ACCOUNT_INACTIVE";
    public static final String ORDER_NOT_OWNED = "ORDER_NOT_OWNED";
    public static final String ORDER_NOT_SUBMITTED = "ORDER_NOT_SUBMITTED";

    public static final String INSTRUMENT_MISSING = "INSTRUMENT_MISSING";
    public static final String ASSET_UNSUPPORTED = "ASSET_UNSUPPORTED";
    public static final String INSTRUMENT_UNAVAILABLE = "INSTRUMENT_UNAVAILABLE";
    public static final String MARKET_UNSUPPORTED = "MARKET_UNSUPPORTED";
    public static final String MARKET_CLOSED = "MARKET_CLOSED";

    public static final String SIDE_MISSING = "SIDE_MISSING";
    public static final String QUANTITY_INVALID = "QUANTITY_INVALID";
    public static final String QUANTITY_PRECISION = "QUANTITY_PRECISION";

    public static final String QUOTE_MISSING = "QUOTE_MISSING";
    public static final String QUOTE_CURRENCY_MISMATCH = "QUOTE_CURRENCY_MISMATCH";
    public static final String QUOTE_STALE = "QUOTE_STALE";

    public static final String INSUFFICIENT_CASH = "INSUFFICIENT_CASH";
    public static final String INSUFFICIENT_UNITS = "INSUFFICIENT_UNITS";

    private OrderValidationErrorCodes() {
    }
}
