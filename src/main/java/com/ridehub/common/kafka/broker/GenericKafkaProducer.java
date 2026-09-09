package com.ridehub.common.kafka.broker;

import com.ridehub.avro.common.EventEnvelope;
import com.ridehub.common.kafka.service.KafkaUtilityService;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Supplier;

/**
 * Generic Kafka producer that supplies EventEnvelope messages via Supplier
 * binding.
 * No StreamBridge usage — messages are queued and emitted by get().
 * 
 * Uses LinkedBlockingQueue instead of AtomicReference to prevent message loss
 * when multiple threads produce events concurrently.
 */
@Component
public class GenericKafkaProducer implements Supplier<Message<EventEnvelope>> {

    private final KafkaUtilityService kafkaUtilityService;
    private final LinkedBlockingQueue<QueuedEvent> eventQueue = new LinkedBlockingQueue<>();
    private static final Logger log = LoggerFactory.getLogger(GenericKafkaProducer.class);

    @Value("${spring.application.name:default-service}")
    private String serviceName;

    public GenericKafkaProducer(KafkaUtilityService kafkaUtilityService) {
        this.kafkaUtilityService = kafkaUtilityService;
    }

    @PostConstruct
    public void validateConfiguration() {
        log.info("GenericKafkaProducer initialized for service: {}", serviceName);
    }

    @Override
    public Message<EventEnvelope> get() {
        QueuedEvent queued = eventQueue.poll();

        if (queued != null) {
            log.info("Supplying event with unique key: {} to reactive stream", queued.key);
            MessageBuilder<EventEnvelope> builder = MessageBuilder.withPayload(queued.envelope)
                    .setHeader(KafkaHeaders.KEY, queued.key)
                    .setHeader("serviceName", serviceName);

            if (queued.correlationId != null) {
                builder.setHeader("X-Correlation-Id", queued.correlationId);
            }
            if (queued.traceparent != null) {
                builder.setHeader("traceparent", queued.traceparent);
            }

            return builder.build();
        } else {
            log.trace("No event to supply, returning null.");
            return null;
        }
    }

    /**
     * Queue event for reactive stream processing.
     * Thread-safe: uses BlockingQueue so concurrent calls won't lose messages.
     */
    protected <T> String queueEvent(String eventName, T payload, String key) {
        try {
            // Single call to AvroConverter (fixed: was previously calling it twice)
            EventEnvelope envelope = kafkaUtilityService.createEventEnvelope(eventName, payload);
            String uniqueKey = kafkaUtilityService.generateEventKey(key);

            if (envelope == null) {
                log.error("Failed to create envelope for event: {}", eventName);
                return null;
            }

            String correlationId = org.slf4j.MDC.get("correlationId");
            String traceparent = org.slf4j.MDC.get("traceparent");

            eventQueue.offer(new QueuedEvent(envelope, uniqueKey, correlationId, traceparent));

            log.info("Event {} queued successfully with key: {}", eventName, uniqueKey);
            return uniqueKey;

        } catch (Exception e) {
            log.error("Error queuing event {}: {}", eventName, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Public send that just queues to the Supplier; no direct broker send.
     */
    public <T> String send(String eventName, T payload, String key) {
        return queueEvent(eventName, payload, key);
    }

    public <T> String send(String eventName, T payload) {
        return send(eventName, payload, null);
    }

    public <T> Optional<String> sendSafely(String eventName, T payload, String key) {
        try {
            return Optional.ofNullable(send(eventName, payload, key));
        } catch (Exception e) {
            log.error("Safe send failed for event {}: {}", eventName, e.getMessage());
            return Optional.empty();
        }
    }

    // Protected helpers for subclasses
    protected KafkaUtilityService getKafkaUtilityService() {
        return kafkaUtilityService;
    }

    protected String getServiceName() {
        return serviceName;
    }

    /**
     * Internal record to hold queued events with their keys and tracing context.
     */
    private record QueuedEvent(EventEnvelope envelope, String key, String correlationId, String traceparent) {
    }
}
