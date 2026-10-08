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

    private ApiRoutes() {
    }
}
