package com.ridehub.common.feign.exception;

import java.net.URI;
import java.util.Collections;
import java.util.Map;

/**
 * Exception representing an RFC 7807 Problem Detail returned by downstream microservices.
 */
public class RidehubApiException extends RuntimeException {

    private final int status;
    private final String title;
    private final String detail;
    private final URI type;
    private final String errorKey;
    private final Map<String, Object> parameters;

    public RidehubApiException(int status, String title, String detail, URI type, String errorKey, Map<String, Object> parameters) {
        super(detail != null ? detail : (title != null ? title : "Downstream service error (" + status + ")"));
        this.status = status;
        this.title = title;
        this.detail = detail;
        this.type = type;
        this.errorKey = errorKey;
        this.parameters = parameters != null ? parameters : Collections.emptyMap();
    }

    public int getStatus() {
        return status;
    }

    public String getTitle() {
        return title;
    }

    public String getDetail() {
        return detail;
    }

    public URI getType() {
        return type;
    }

    public String getErrorKey() {
        return errorKey;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    @Override
    public String toString() {
        return "RidehubApiException{" +
                "status=" + status +
                ", title='" + title + '\'' +
                ", detail='" + detail + '\'' +
                ", errorKey='" + errorKey + '\'' +
                '}';
    }
}
