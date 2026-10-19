package com.neueda.leap.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@ConditionalOnProperty(
        name = "trading.execution.enabled",
        havingValue = "true"
)
public class OrderExecutionSchedulingConfig {
}
