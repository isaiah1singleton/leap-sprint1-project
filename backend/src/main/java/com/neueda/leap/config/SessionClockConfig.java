package com.neueda.leap.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SessionClockConfig {

    @Bean
    public Clock sessionClock() {
        return Clock.systemUTC();
    }
}
