package com.ridehub.common.kafka.handler;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

@Component
public class EventDispatcher {
    private static final Logger LOG = LoggerFactory.getLogger(EventDispatcher.class);
    
    private final Map<String, EventHandler<?>> handlers;
    ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    public EventDispatcher(List<EventHandler<?>> list) {
        this.handlers = list.stream()
                .collect(Collectors.toMap(EventHandler::getEventName, h -> h));
    }

    /**
     * Dispatches events from JSON format
     * 
     * @param env The event message containing JsonNode payload
     * @throws Exception If dispatching fails
     */
    public void dispatch(EventMessage<JsonNode> env) throws Exception {
        String name = env.getEventName();
        LOG.debug("Dispatching event: {}", name);
        
        EventHandler<?> handler = handlers.get(name);
        if (handler == null)
            throw new IllegalArgumentException("No handler for event: " + name);

        // Resolve the generic type parameter T from EventHandler<T>,
        // handling CGLIB proxies and multiple interfaces safely
        JavaType type = mapper.getTypeFactory()
                .constructType(resolveEventHandlerType(handler));
        
        Object dto = mapper.treeToValue(env.getPayload(), type);
        LOG.debug("Converted payload to type: {}", type);

        // noinspection unchecked
        ((EventHandler<Object>) handler).handle(dto);
        LOG.debug("Event handled successfully");
    }

    /**
     * Resolves the generic type argument T from EventHandler<T>, safely handling
     * CGLIB proxies, multiple interfaces, and abstract base classes.
     */
    private Type resolveEventHandlerType(EventHandler<?> handler) {
        Class<?> clazz = handler.getClass();

        // If it's a Spring CGLIB proxy, use the superclass (the actual bean class)
        if (clazz.getName().contains("$$")) {
            clazz = clazz.getSuperclass();
        }

        // Search through all generic interfaces for EventHandler<T>
        for (Type iface : clazz.getGenericInterfaces()) {
            if (iface instanceof ParameterizedType pt) {
                if (EventHandler.class.isAssignableFrom((Class<?>) pt.getRawType())) {
                    return pt.getActualTypeArguments()[0];
                }
            }
        }

        // Fallback: check superclass chain
        Type superclass = clazz.getGenericSuperclass();
        while (superclass != null) {
            if (superclass instanceof ParameterizedType pt) {
                Type raw = pt.getRawType();
                if (raw instanceof Class<?> rawClass) {
                    // Check if superclass itself implements EventHandler
                    for (Type superIface : rawClass.getGenericInterfaces()) {
                        if (superIface instanceof ParameterizedType spt
                                && EventHandler.class.isAssignableFrom((Class<?>) spt.getRawType())) {
                            return pt.getActualTypeArguments()[0];
                        }
                    }
                }
            }
            if (superclass instanceof Class<?> sc) {
                superclass = sc.getGenericSuperclass();
            } else {
                break;
            }
        }

        throw new IllegalStateException(
                "Cannot resolve generic type for EventHandler implementation: " + handler.getClass().getName());
    }
}
