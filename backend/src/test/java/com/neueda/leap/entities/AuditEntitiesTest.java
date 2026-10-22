package com.neueda.leap.entities;

import com.neueda.leap.enums.AccountStatus;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static com.neueda.leap.support.TradingFixtures.*;
import static org.assertj.core.api.Assertions.*;

@Tag("unit")
class AuditEntitiesTest {
    @Test void outboxRoundTripsEventAndRetainsRetryHistory() {
        var event = audit(); AuditOutbox row = new AuditOutbox(event);
        assertThat(row.event()).isEqualTo(event); assertThat(row.getAttemptCount()).isZero();
        assertThat(row.getPublishedAt()).isNull(); assertThat(row.getLastError()).isNull();
        row.markFailure(null); assertThat(row.getLastError()).isEqualTo("Kafka publish failed.");
        row.markFailure("x".repeat(1001)); assertThat(row.getLastError()).hasSize(1000);
        row.markFailure("timeout"); assertThat(row.getAttemptCount()).isEqualTo(3);
        row.markPublished(AT); assertThat(row.getPublishedAt()).isEqualTo(AT); assertThat(row.getLastError()).isNull();
        assertThat(row.getAttemptCount()).isEqualTo(3);
    }
    @Test void auditRecordPreservesBusinessPayload() {
        var e = audit(); AuditRecord r = new AuditRecord(e, AT);
        assertThat(r.getEventId()).isEqualTo(e.eventId()); assertThat(r.getOrderId()).isEqualTo(e.orderId());
        assertThat(r.getEventType()).isEqualTo(e.eventType()); assertThat(r.getStatus()).isEqualTo(e.status());
        assertThat(r.getAccountId()).isEqualTo(e.accountId()); assertThat(r.getClientId()).isEqualTo(e.clientId());
        assertThat(r.getClientEmail()).isEqualTo(e.clientEmail()); assertThat(r.getClientName()).isEqualTo(e.clientName());
        assertThat(r.getInstrument()).isEqualTo(e.instrument()); assertThat(r.getSide()).isEqualTo(e.side());
        assertThat(r.getQuantity()).isEqualTo(e.quantity()); assertThat(r.getPrice()).isEqualTo(e.price());
        assertThat(r.getOccurredAt()).isEqualTo(e.occurredAt()); assertThat(r.getReason()).isEqualTo(e.reason());
        SuspiciousTradeFlag flag = new SuspiciousTradeFlag(3, 1, "admin", AT, AccountStatus.ACTIVE);
        assertThat(flag.getOrderId()).isEqualTo(3); assertThat(flag.getAccountId()).isEqualTo(1);
        assertThat(flag.getPreviousAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
    }
    @Test void legacySessionRecordsActivityAndRevocationWithoutChangingExpiry() {
        AuthSession s = new AuthSession("hash", client(), AT, AT.plusDays(1));
        assertThat(s.isRevoked()).isFalse(); assertThat(s.getLastActivityAt()).isEqualTo(AT);
        s.recordActivity(AT.plusMinutes(1)); s.revoke(AT.plusMinutes(2));
        assertThat(s.getClient().getClientId()).isEqualTo(7); assertThat(s.getCreatedAt()).isEqualTo(AT);
        assertThat(s.getLastActivityAt()).isEqualTo(AT.plusMinutes(1)); assertThat(s.isRevoked()).isTrue();
        assertThat(s.getRevokedAt()).isEqualTo(AT.plusMinutes(2)); assertThat(s.getExpiresAt()).isEqualTo(AT.plusDays(1));
    }
}
