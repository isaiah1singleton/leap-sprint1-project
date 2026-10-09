package com.neueda.leap;

import com.neueda.leap.entities.OutboxEvent;
import com.neueda.leap.repository.OutboxEventRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:outbox;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=file:../database/transaction_schema.sql"
})
class OutboxPersistenceTest {
    @Autowired private OutboxEventRepository repository;
    @Autowired private EntityManager entityManager;

    @Test
    @Transactional
    void persistsAcceptedOrderPayloadAsJson() {
        OutboxEvent event = repository.saveAndFlush(OutboxEvent.orderAccepted(42));
        entityManager.clear();

        OutboxEvent saved = repository.findById(event.getEventId()).orElseThrow();
        assertThat(saved.getPayload().get("orderId").asInt()).isEqualTo(42);
        assertThat(saved.getPublishedAt()).isNull();
    }
}
