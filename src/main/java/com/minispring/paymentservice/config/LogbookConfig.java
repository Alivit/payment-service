package com.minispring.paymentservice.config;

import java.util.Set;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.zalando.logbook.BodyFilter;
import org.zalando.logbook.CorrelationId;
import org.zalando.logbook.HeaderFilter;
import org.zalando.logbook.core.HeaderFilters;
import org.zalando.logbook.json.JsonBodyFilters;

@Configuration
public class LogbookConfig {

    private static final String TRACE_ID_KEY = "traceId";

    @Bean
    public CorrelationId customCorrelationId() {
        return _ -> {
            String traceId = MDC.get(TRACE_ID_KEY);
            return traceId != null ? traceId : UUID.randomUUID().toString();
        };
    }

    @Bean
    public BodyFilter customBodyFilter() {
        return JsonBodyFilters.replaceJsonStringProperty(
                Set.of("cardNumber", "cvv", "password", "refreshToken"), "**********");
    }

    @Bean
    public HeaderFilter customHeaderFilter() {
        return HeaderFilters.replaceHeaders(Set.of("Authorization", "X-API-Key", "Cookie"), "**********");
    }
}
