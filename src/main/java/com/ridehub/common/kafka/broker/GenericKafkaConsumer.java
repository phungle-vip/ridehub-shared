package com.ridehub.common.kafka.broker;

import com.ridehub.avro.common.EventEnvelope;
import com.ridehub.common.kafka.service.KafkaUtilityService;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;

public abstract class GenericKafkaConsumer implements Consumer<Message<EventEnvelope>> {
    private static final Logger LOG = LoggerFactory.getLogger(GenericKafkaConsumer.class);
    protected final KafkaUtilityService kafkaUtilityService;

    protected GenericKafkaConsumer(KafkaUtilityService kafkaUtilityService) {
        this.kafkaUtilityService = kafkaUtilityService;
    }

    public final void accept(Message<EventEnvelope> message) {
        String correlationId = null;
        String traceparent = null;
        try {
            if (message == null || message.getPayload() == null) {
                LOG.error("Received null message or payload");
                throw new IllegalArgumentException("Received null message or payload");
            }

            // Extract distributed tracing headers from Kafka message
            Object corrHeader = message.getHeaders().get("X-Correlation-Id");
            correlationId = corrHeader != null ? corrHeader.toString() : null;
            Object traceHeader = message.getHeaders().get("traceparent");
            traceparent = traceHeader != null ? traceHeader.toString() : null;

            if (correlationId != null) {
                org.slf4j.MDC.put("correlationId", correlationId);
                org.slf4j.MDC.put("traceId", correlationId);
            }
            if (traceparent != null) {
                org.slf4j.MDC.put("traceparent", traceparent);
                String[] parts = traceparent.split("-");
                if (parts.length >= 4) {
                    org.slf4j.MDC.put("traceId", parts[1]);
                    org.slf4j.MDC.put("spanId", parts[2]);
                }
            }

            EventEnvelope envelope = message.getPayload();
            Object keyObject = message.getHeaders().get("kafka_receivedMessageKey");
            String key = keyObject != null ? keyObject.toString() : null;

            LOG.debug("Received event [{}] with key [{}]", envelope.getEventName(), key);

            // Process synchronously - any exception will trigger Spring Cloud Stream DLQ / DLT handling
            this.kafkaUtilityService.processMessage(envelope);

            LOG.debug("Processing completed successfully for event: {}", envelope.getEventName());

        } catch (Exception e) {
            LOG.error("Error handling message (will trigger retry and route to DLT if max retries exceeded): {}", e.getMessage(), e);
            // Re-throw to trigger Spring Cloud Stream Dead Letter Topic (DLT) error handler
            throw e;
        } finally {
            if (correlationId != null) {
                org.slf4j.MDC.remove("correlationId");
            }
            if (traceparent != null) {
                org.slf4j.MDC.remove("traceparent");
                org.slf4j.MDC.remove("spanId");
            }
            org.slf4j.MDC.remove("traceId");
        }
    }
}