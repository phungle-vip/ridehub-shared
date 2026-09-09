package com.ridehub.common.feign.exception;

import java.net.URI;
import java.util.Map;

public class InvalidPromotionException extends RidehubApiException {
    public InvalidPromotionException(int status, String title, String detail, URI type, String errorKey, Map<String, Object> parameters) {
        super(status, title != null ? title : "Invalid Promotion", detail, type, errorKey, parameters);
    }
}
