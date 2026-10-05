package com.github.lechandonga.ephemeralenvironmentorchestratorjava.config;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(OrchestratorProperties.class)
public class OrchestratorConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
