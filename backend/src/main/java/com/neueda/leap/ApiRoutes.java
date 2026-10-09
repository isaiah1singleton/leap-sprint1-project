package com.neueda.leap;

public final class ApiRoutes {
    public static final String HELLO = "/hello";
    public static final String AUTH = "/api/auth";
    public static final String REGISTER = "/register";
    public static final String SIGN_IN = "/sign-in";
    public static final String SIGN_OUT = "/sign-out";
    public static final String AUTH_REGISTER = AUTH + REGISTER;
    public static final String AUTH_SIGN_IN = AUTH + SIGN_IN;
    public static final String ORDER_VALIDATE = "/api/orders/{orderId}/validate";
    public static final String ORDERS = "/api/orders";
    public static final String ORDER = "/{orderId}";
    public static final String ORDER_CANCEL = ORDER + "/cancel";
    public static final String ACCOUNTS = "/api/accounts";
    public static final String ACCOUNT_BALANCE = "/{accountId}/balance";
    public static final String ACCOUNT_HOLDINGS = "/{accountId}/holdings";
    public static final String ACCOUNT_TRANSFERS = "/{accountId}/transfers";
    public static final String MARKET = "/api/market";
    public static final String MARKET_SYMBOLS = "/symbols";
    public static final String MARKET_QUOTES = "/quotes";
    public static final String MARKET_QUOTE = MARKET_QUOTES + "/{symbol}";
    public static final String MARKET_ALL = MARKET + "/**";

    private ApiRoutes() {
    }
}
