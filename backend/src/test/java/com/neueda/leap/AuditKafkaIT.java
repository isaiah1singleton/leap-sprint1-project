package com.neueda.leap;

import com.neueda.leap.models.TradeAuditEvent;
import com.neueda.leap.repository.AuditRecordRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:audit-kafka;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=validate", "spring.sql.init.mode=always",
    "spring.sql.init.schema-locations=file:../database/transaction_schema.sql",
    "spring.kafka.listener.auto-startup=true", "audit.kafka.topic=test.audit",
    "spring.kafka.consumer.group-id=integration-audit", "spring.kafka.consumer.auto-offset-reset=earliest",
    "spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer",
    "spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JsonSerializer",
    "spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
    "spring.kafka.consumer.value-deserializer=org.springframework.kafka.support.serializer.JsonDeserializer",
    "spring.kafka.consumer.properties.spring.json.trusted.packages=com.neueda.leap.models",
    "spring.kafka.consumer.properties.spring.json.value.default.type=com.neueda.leap.models.TradeAuditEvent",
    "logging.level.org.apache.kafka=ERROR", "logging.level.kafka=ERROR"
})
@EmbeddedKafka(partitions = 1, topics = "test.audit", bootstrapServersProperty = "spring.kafka.bootstrap-servers")
@DirtiesContext
class AuditKafkaIT {
    @Autowired KafkaTemplate<String, Object> kafka;
    @Autowired AuditRecordRepository records;
    @Autowired com.neueda.leap.service.AuditOutboxPublisher publisher;
    @Autowired com.neueda.leap.repository.AuditOutboxRepository outbox;
    @Autowired com.neueda.leap.repository.ClientRepository clients;
    @Autowired com.neueda.leap.repository.AccountRepository accounts;
    @Autowired com.neueda.leap.repository.InstrumentRepository instruments;
    @Autowired com.neueda.leap.repository.OrderRepository orders;

    @Test
    void publishesACommittedOutboxRowAndMarksItAfterAcknowledgement() {
        var client = clients.save(new com.neueda.leap.entities.Client("Kafka test", UUID.randomUUID() + "@example.test", "hash"));
        var account = accounts.save(new com.neueda.leap.entities.Account(client, "Trading"));
        var instrument = instruments.save(new com.neueda.leap.entities.Instrument("CRYPTO", "BTC", "Bitcoin",
                com.neueda.leap.enums.AssetClass.CRYPTO, com.neueda.leap.enums.Currency.USD));
        var now = OffsetDateTime.now().minusSeconds(1);
        var order = orders.save(new com.neueda.leap.entities.Order(account, instrument, com.neueda.leap.enums.OrderSide.BUY,
                BigDecimal.ONE, BigDecimal.TEN, now, now));
        var event = new TradeAuditEvent(UUID.randomUUID(), "ORDER_STATUS_CHANGED", now, order.getOrderId(), account.getAccountId(),
                client.getClientId(), client.getEmail(), client.getClientName(), instrument.getSymbol(), "BUY", BigDecimal.ONE,
                BigDecimal.TEN, "FILLED", null);
        var row = outbox.saveAndFlush(new com.neueda.leap.entities.AuditOutbox(event));
        publisher.publishPending();
        assertThat(outbox.findById(row.getOutboxId()).orElseThrow().getPublishedAt()).isNotNull();
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(records.existsByEventId(event.eventId())).isTrue());
    }

    @Test
    void serializesAnEventThroughTheRealBrokerAndPersistsOneRecordAfterRedelivery() throws Exception {
        var event = new TradeAuditEvent(UUID.randomUUID(), "ORDER_STATUS_CHANGED", OffsetDateTime.now(),
                7, 1, 1, "test@example.test", "Trader", "AAPL", "BUY", new BigDecimal("1.25"),
                new BigDecimal("10.125"), "FILLED", null);
        kafka.send("test.audit", "7", event).get(10, java.util.concurrent.TimeUnit.SECONDS);
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(records.existsByEventId(event.eventId())).isTrue());
        kafka.send("test.audit", "7", event).get(10, java.util.concurrent.TimeUnit.SECONDS);
        var sentinel = new TradeAuditEvent(UUID.randomUUID(), event.eventType(), event.occurredAt(), 7, 1, 1,
                event.clientEmail(), event.clientName(), event.instrument(), event.side(), BigDecimal.ONE,
                BigDecimal.TEN, "FILLED", null);
        // Same key and partition: observing the sentinel proves the preceding duplicate was consumed.
        kafka.send("test.audit", "7", sentinel).get(10, java.util.concurrent.TimeUnit.SECONDS);
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(records.existsByEventId(sentinel.eventId())).isTrue());
        var rows = records.findAllByOrderIdOrderByOccurredAtAsc(7);
        assertThat(rows).hasSize(2);
        var stored = rows.stream().filter(row -> row.getEventId().equals(event.eventId())).findFirst().orElseThrow();
        assertThat(stored.getQuantity()).isEqualByComparingTo("1.25");
        assertThat(stored.getPrice()).isEqualByComparingTo("10.125");
    }
}
