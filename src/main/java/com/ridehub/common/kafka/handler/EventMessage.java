package com.ridehub.common.kafka.handler;

// wraps any payload with an eventName — renamed from EventEnvelope to avoid
// conflict with com.ridehub.avro.common.EventEnvelope (Avro record)

public class EventMessage<T> {
    private String eventName;
    private T payload;

    public EventMessage() {
    }

    public EventMessage(String eventName, T payload) {
        this.eventName = eventName;
        this.payload = payload;
    }

    public String getEventName() {
        return eventName;
    }

    public void setEventName(String eventName) {
        this.eventName = eventName;
    }

    public T getPayload() {
        return payload;
    }

    public void setPayload(T payload) {
        this.payload = payload;
    }

}
