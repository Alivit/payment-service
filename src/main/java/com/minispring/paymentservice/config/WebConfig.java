package com.minispring.paymentservice.config;

import com.minispring.paymentservice.model.PaymentStatus;
import org.jspecify.annotations.NonNull;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addFormatters(@NonNull FormatterRegistry registry) {
        registry.addConverter(new StringToPaymentStatusConverter());
    }

    private static class StringToPaymentStatusConverter implements Converter<String, PaymentStatus> {
        @Override
        public PaymentStatus convert(String source) {
            return PaymentStatus.valueOf(source.trim().toUpperCase());
        }
    }
}
