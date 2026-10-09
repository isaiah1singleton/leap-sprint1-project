package com.neueda.leap;

import com.neueda.leap.entities.OutboxEvent;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OutboxEventTest {
    @Test
    void acceptedOrderCarriesItsIdentityAndStartsUnpublished() {
        OutboxEvent event = OutboxEvent.orderAccepted(42);

        assertThat(event.getEventType()).isEqualTo(OutboxEvent.ORDER_ACCEPTED);
        assertThat(event.getAggregateType()).isEqualTo("Order");
        assertThat(event.getAggregateId()).isEqualTo("42");
        assertThat(event.getPayload().get("eventId").asText()).isEqualTo(event.getEventId().toString());
        assertThat(event.getPayload().get("orderId").asInt()).isEqualTo(42);
        assertThat(event.getPayload().get("schemaVersion").asInt()).isEqualTo(1);
        assertThat(event.getPublishedAt()).isNull();

        event.markPublished(event.getCreatedAt().plusSeconds(1));
        assertThat(event.getPublishedAt()).isAfter(event.getCreatedAt());
        assertThatThrownBy(() -> event.markPublished(OffsetDateTime.now()))
                .isInstanceOf(IllegalStateException.class);
    }
}
