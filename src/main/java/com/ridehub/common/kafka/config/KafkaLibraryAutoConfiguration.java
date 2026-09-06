package com.ridehub.common.kafka.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ridehub.common.kafka.broker.GenericKafkaProducer;
import com.ridehub.common.kafka.handler.EventDispatcher;
import com.ridehub.common.kafka.handler.EventHandler;
import com.ridehub.common.kafka.runner.KafkaJobRunner;
import com.ridehub.common.kafka.service.KafkaUtilityService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.retry.RetryPolicy;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Auto-configuration for the Kafka library.
 * This will be automatically loaded by Spring Boot when the library is on the
 * classpath.
 */
@Configuration
@EnableConfigurationProperties({ KafkaLibraryProperties.class })
public class KafkaLibraryAutoConfiguration {

    @Bean(name = "kafkaObjectMapper")
    public ObjectMapper kafkaObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        return objectMapper;
    }

    @Bean
    @ConditionalOnMissingBean
    public RetryTemplate kafkaRetryTemplate() {
        RetryTemplate retryTemplate = new RetryTemplate();

        // Retry policy: retry up to 3 times
        RetryPolicy retryPolicy = new SimpleRetryPolicy(3);
        retryTemplate.setRetryPolicy(retryPolicy);

        // Exponential backoff policy: initial delay 1s, max delay 10s, multiplier 2
        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(1000);
        backOffPolicy.setMaxInterval(10000);
        backOffPolicy.setMultiplier(2);
        retryTemplate.setBackOffPolicy(backOffPolicy);

        return retryTemplate;
    }

    @Bean
    @ConditionalOnMissingBean
    public Map<String, SseEmitter> sseEmitters() {
        return new HashMap<>();
    }

    @Bean
    @ConditionalOnMissingBean
    public EventDispatcher eventDispatcher(List<EventHandler<?>> eventHandlers) {
        return new EventDispatcher(eventHandlers);
    }

    @Bean
    @ConditionalOnMissingBean
    public KafkaJobRunner kafkaJobRunner(StreamBridge streamBridge, KafkaLibraryProperties properties) {
        return new KafkaJobRunner(streamBridge, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public KafkaUtilityService kafkaUtilityService(
            EventDispatcher dispatcher,
            Map<String, SseEmitter> emitters,
            @Qualifier("kafkaObjectMapper") ObjectMapper objectMapper,
            KafkaLibraryProperties properties) {
        return new KafkaUtilityService(dispatcher, emitters, objectMapper,
                properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public GenericKafkaProducer genericKafkaProducer(
            KafkaUtilityService kafkaUtilityService) {
        return new GenericKafkaProducer(kafkaUtilityService);
    }
}
