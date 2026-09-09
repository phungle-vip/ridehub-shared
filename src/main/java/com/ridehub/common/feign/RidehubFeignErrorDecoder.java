package com.ridehub.common.feign;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ridehub.common.feign.exception.*;
import feign.Response;
import feign.codec.ErrorDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Smart Feign ErrorDecoder translating RFC 7807 ProblemDetails from downstream providers
 * into domain-specific exceptions on the consumer side.
 */
public class RidehubFeignErrorDecoder implements ErrorDecoder {

    private static final Logger log = LoggerFactory.getLogger(RidehubFeignErrorDecoder.class);
    private final ErrorDecoder defaultDecoder = new Default();
    private final ObjectMapper objectMapper;

    public RidehubFeignErrorDecoder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    @Override
    public Exception decode(String methodKey, Response response) {
        int status = response.status();

        try {
            if (response.body() != null) {
                InputStream inputStream = response.body().asInputStream();
                String body = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);

                if (!body.isBlank()) {
                    JsonNode node = objectMapper.readTree(body);

                    String title = node.has("title") ? node.get("title").asText() : null;
                    String detail = node.has("detail") ? node.get("detail").asText() : (node.has("message") ? node.get("message").asText() : null);
                    String errorKey = node.has("errorKey") ? node.get("errorKey").asText() : null;
                    URI type = node.has("type") ? URI.create(node.get("type").asText()) : null;

                    Map<String, Object> params = new HashMap<>();
                    if (node.has("params") && node.get("params").isObject()) {
                        Iterator<Map.Entry<String, JsonNode>> fields = node.get("params").fields();
                        while (fields.hasNext()) {
                            Map.Entry<String, JsonNode> entry = fields.next();
                            params.put(entry.getKey(), entry.getValue().asText());
                        }
                    }

                    // Categorize into specific business exceptions
                    String combined = ((title != null ? title : "") + " " + (detail != null ? detail : "") + " " + (errorKey != null ? errorKey : "")).toLowerCase();

                    if (status == 404) {
                        return new ResourceNotFoundException(title, detail, type, errorKey, params);
                    }

                    if (status == 409 || combined.contains("seat") || combined.contains("locked")) {
                        if (combined.contains("seat")) {
                            return new SeatAlreadyLockedException(title, detail, type, errorKey, params);
                        }
                        return new RidehubConflictException(title, detail, type, errorKey, params);
                    }

                    if (combined.contains("promotion") || combined.contains("voucher") || combined.contains("coupon")) {
                        return new InvalidPromotionException(status, title, detail, type, errorKey, params);
                    }

                    return new RidehubApiException(status, title, detail, type, errorKey, params);
                }
            }
        } catch (Exception ex) {
            log.debug("Failed to decode RFC 7807 problem details for method {}: {}", methodKey, ex.getMessage());
        }

        return defaultDecoder.decode(methodKey, response);
    }
}
