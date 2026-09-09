package com.ridehub.common.feign.exception;

import java.net.URI;
import java.util.Map;

public class ResourceNotFoundException extends RidehubApiException {
    public ResourceNotFoundException(String title, String detail, URI type, String errorKey, Map<String, Object> parameters) {
        super(404, title, detail, type, errorKey, parameters);
    }
}
