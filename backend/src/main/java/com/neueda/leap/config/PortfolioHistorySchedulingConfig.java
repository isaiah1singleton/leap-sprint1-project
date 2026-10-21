package com.neueda.leap.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "portfolio.history.enabled", havingValue = "true")
public class PortfolioHistorySchedulingConfig {
    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("trading-scheduler-");
        return scheduler;
    }

    // Provider waits in history recording must not delay execution or audit publishing.
    @Bean
    public ThreadPoolTaskScheduler portfolioHistoryScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("portfolio-history-");
        return scheduler;
    }
}
