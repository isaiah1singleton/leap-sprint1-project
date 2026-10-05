package com.neueda.leap.models;

import java.time.OffsetDateTime;

public record AuthResponse(
        Integer clientId,
        String clientName,
        String email,
        String clientSegment,
        String accessToken,
        String tokenType,
        OffsetDateTime expiresAt) {
}
