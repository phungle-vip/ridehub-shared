package com.ridehub.common.kafka.service;

import com.ridehub.avro.common.EventEnvelope;
import com.ridehub.common.kafka.config.KafkaLibraryProperties;
import com.ridehub.common.kafka.handler.EventDispatcher;
import com.ridehub.common.kafka.handler.EventMessage;
import com.ridehub.common.kafka.util.AvroConverter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Generic Kafka utility service that can work with any payload type
 * Modified for Spring Cloud Stream built-in DLQ handling
 */
@Service
public class KafkaUtilityService {

    private static final Logger PRODUCER_LOG = LoggerFactory.getLogger("KafkaProducerHelper");
    private static final Logger CONSUMER_LOG = LoggerFactory.getLogger("KafkaConsumerHelper");

    private final ObjectMapper objectMapper;
    private final EventDispatcher dispatcher;
    private final Map<String, SseEmitter> emitters;
    private final KafkaLibraryProperties properties;

    public KafkaUtilityService(
            EventDispatcher dispatcher,
            Map<String, SseEmitter> emitters,
            ObjectMapper objectMapper,
            KafkaLibraryProperties properties) {
        this.objectMapper = objectMapper;
        this.dispatcher = dispatcher;
        this.emitters = emitters;
        this.properties = properties;
    }

    // --- Generic Helper for KafkaProducer ---

    /**
     * Create an EventEnvelope for any payload type
     */
    public <T> EventEnvelope createEventEnvelope(String eventName, T payload) {
        return AvroConverter.createEvent(eventName, payload);
    }

    /**
     * Generate a unique key for the event message
     */
    public String generateEventKey(String key) {
        String uniqueKey = (key != null) ? key : UUID.randomUUID().toString();
        PRODUCER_LOG.info("Generated key for Kafka message: {}", uniqueKey);
        return uniqueKey;
    }

    // --- Consumer Helpers - Simplified for Spring Cloud Stream DLQ ---

    /**
     * Process message synchronously - exceptions will trigger Spring Cloud Stream
     * DLQ.
     * 
     * Optimized flow: Avro payload JSON string → JsonNode → dispatch
     * (eliminated intermediate Class.forName + re-serialize steps)
     */
    public void processMessage(EventEnvelope avroMessage) {
        String eventName = null;

        try {
            eventName = avroMessage.getEventName() != null
                    ? avroMessage.getEventName().toString()
                    : null;

            if (eventName == null) {
                throw new IllegalArgumentException("Event name cannot be null");
            }

            CONSUMER_LOG.debug("Processing message for event: {}", eventName);

            // Optimized: parse Avro payload JSON string directly to JsonNode
            // instead of Class.forName → Object → writeValueAsString → readTree
            JsonNode payloadNode = null;
            if (avroMessage.getPayload() != null) {
                payloadNode = objectMapper.readTree(avroMessage.getPayload().toString());
            }

            // Build EventMessage for dispatcher and dispatch
            EventMessage<JsonNode> dispatcherMessage = new EventMessage<>(eventName, payloadNode);
            dispatcher.dispatch(dispatcherMessage);

            // SSE broadcast to clients (optional visibility)
            dispatchToSseClients(eventName, payloadNode, emitters);

            CONSUMER_LOG.info("Successfully processed message for event: {}", eventName);

            // Completion notification
            notifyProcessingComplete(eventName);

        } catch (Exception e) {
            CONSUMER_LOG.error("Error processing message (event: {}): {}",
                    (eventName != null ? eventName : "UNKNOWN_EVENT"), e.getMessage(), e);

            // Re-throw exception to trigger Spring Cloud Stream DLQ handling
            throw new RuntimeException("Message processing failed for event: " + eventName, e);
        }
    }

    // --- SSE Helper Methods ---

    public SseEmitter registerSseEmitter(String key) {
        CONSUMER_LOG.debug("Registering SSE client for {}", key);
        SseEmitter emitter = new SseEmitter();
        emitter.onCompletion(() -> {
            CONSUMER_LOG.debug("SSE Emitter completed for key {}. Removing from map.", key);
            emitters.remove(key);
        });
        emitters.put(key, emitter);
        return emitter;
    }

    public void unregisterSseEmitter(String key) {
        CONSUMER_LOG.debug("Unregistering SSE emitter for: {}", key);
        SseEmitter emitter = emitters.get(key);
        if (emitter != null) {
            emitter.complete();
        } else {
            CONSUMER_LOG.warn("No SSE emitter found for key {} to unregister.", key);
        }
    }

    public void dispatchToSseClients(String eventName, Object payload) {
        dispatchToSseClients(eventName, payload, emitters);
    }

    public void dispatchToSseClients(String eventName, Object payload, Map<String, SseEmitter> emittersMap) {
        CONSUMER_LOG.debug("Dispatching event {} to {} SSE clients", eventName, emittersMap.size());
        emittersMap.forEach((key, emitter) -> {
            try {
                emitter.send(
                        SseEmitter.event()
                                .name(eventName)
                                .data(payload != null ? payload : "No payload", MediaType.APPLICATION_JSON));
                CONSUMER_LOG.trace("Successfully sent event {} to SSE client with key {}", eventName, key);
            } catch (IOException | IllegalStateException ex) {
                CONSUMER_LOG.debug("Failed to send to SSE client with key {}, removing emitter. Error: {}", key,
                        ex.getMessage());
            }
        });
    }

    // --- Helper Methods ---

    public Map<String, SseEmitter> getEmitters() {
        return emitters;
    }

    private void notifyProcessingComplete(String eventName) {
        try {
            Map<String, Object> info = new HashMap<>();
            info.put("event", eventName);
            info.put("status", "completed");
            info.put("id", UUID.randomUUID().toString());
            dispatchToSseClients("processing.complete", info);
            CONSUMER_LOG.info("Processing complete notification emitted for event {}", eventName);
        } catch (Exception e) {
            CONSUMER_LOG.debug("Failed to emit processing completion SSE: {}", e.getMessage());
        }
    }
}