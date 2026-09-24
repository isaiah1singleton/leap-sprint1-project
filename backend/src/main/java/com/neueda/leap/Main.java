package com.neueda.leap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@RestController
public class Main {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(Main.class);
        application.addInitializers((ConfigurableApplicationContext context) ->
                logDatabaseTarget(context.getEnvironment()));
        application.run(args);
    }

    private static void logDatabaseTarget(Environment environment) {
        String url = environment.getProperty("spring.datasource.url");
        String username = environment.getProperty("spring.datasource.username", "<not configured>");
        logger.info("Database target: url={}, username={}", safeJdbcUrl(url), username);
    }

    private static String safeJdbcUrl(String url) {
        if (url == null) {
            return "<not configured>";
        }

        int queryStart = url.indexOf('?');
        String withoutQuery = queryStart >= 0 ? url.substring(0, queryStart) : url;
        return withoutQuery.replaceFirst("(?i)(jdbc:postgresql://)[^/@]+@", "$1****@");
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @GetMapping("/hello")
    public String sayHello(
            @RequestParam(value = "myName", defaultValue = "World") String name) {
        return String.format("Hello %s!", name);
    }
}

