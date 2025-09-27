package com.example.api.config;

import org.zalando.logbook.*;
import org.zalando.logbook.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.Set;

@Configuration
class HttpLoggingConfig {

    @Bean
    Logbook logbook() {
        return Logbook.builder()
                .strategy(new WithoutBodyStrategy())
                .headerFilter(HeaderFilters.authorization())
                .build();
    }
}
