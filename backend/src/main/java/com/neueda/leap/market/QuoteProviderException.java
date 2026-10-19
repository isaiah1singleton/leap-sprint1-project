package com.neueda.leap.market;

import java.time.Instant;
import java.util.Objects;

public class QuoteProviderException extends RuntimeException {

    public enum Kind {
        CONFIGURATION,
        NOT_FOUND,
        RATE_LIMITED,
        UNAVAILABLE,
        INVALID_RESPONSE
    }

    private final Kind kind;
    private final Instant retryAt;

    public QuoteProviderException(Kind kind, String message) {
        this(kind, message, null);
    }

    public QuoteProviderException(Kind kind, String message, Instant retryAt) {
        super(message);
        this.kind = Objects.requireNonNull(kind);
        this.retryAt = retryAt;
    }

    public Kind kind() {
        return kind;
    }

    public Instant retryAt() {
        return retryAt;
    }
}
