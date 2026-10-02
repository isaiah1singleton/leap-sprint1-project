package com.neueda.leap.entities.test;

import com.neueda.leap.entities.Instrument;
import com.neueda.leap.enums.OrderSide;
import com.neueda.leap.entities.Order;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.util.Assert;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.*;

public class OrderTest {
    @Mock
    private Instrument mockInstrument;

    private AutoCloseable mocks;

    @BeforeEach
    void beforeEachSetup(){
        mocks = MockitoAnnotations.openMocks(this);
    }

    @Nested
    class ConstructorTests {
        // Test if an ordinary order can be submitted
        @Test
        void validOrderConstructed() {
            assertDoesNotThrow(
                    () -> new Order(
                            mockInstrument,
                            OrderSide.BUY,
                            new BigDecimal(10),
                            OffsetDateTime.now()
                    )
            );
        }

        @Test
        void missingRequiredArguments() {
            assertThrows(IllegalArgumentException.class,
                    () -> new Order(
                            null,
                            OrderSide.BUY,
                            new BigDecimal(10),
                            OffsetDateTime.now()
                    )
            );
            assertThrows(IllegalArgumentException.class,
                    () -> new Order(
                            mockInstrument,
                            null,
                            new BigDecimal(10),
                            OffsetDateTime.now()
                    )
            );
            assertThrows(IllegalArgumentException.class,
                    () -> new Order(
                            mockInstrument,
                            OrderSide.BUY,
                            new BigDecimal(10),
                            null
                    )
            );
        }

        @Test
        void nonPositiveQuantity() {
            assertThrows(IllegalArgumentException.class,
                    () -> new Order(
                            mockInstrument,
                            OrderSide.BUY,
                            new BigDecimal(0),
                            OffsetDateTime.now()
                    )
            );
            assertThrows(IllegalArgumentException.class,
                    () -> new Order(
                            mockInstrument,
                            OrderSide.BUY,
                            new BigDecimal(-2),
                            OffsetDateTime.now()
                    )
            );
        }
    }


    @AfterEach
    void afterEachTeardown() throws Exception{
        mocks.close();
    }
}
