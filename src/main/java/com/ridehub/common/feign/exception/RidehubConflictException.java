package com.ridehub.common.feign.exception;

import java.net.URI;
import java.util.Map;

public class RidehubConflictException extends RidehubApiException {
    public RidehubConflictException(String title, String detail, URI type, String errorKey, Map<String, Object> parameters) {
        super(409, title, detail, type, errorKey, parameters);
    }
}
