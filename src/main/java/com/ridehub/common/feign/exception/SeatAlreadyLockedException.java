package com.ridehub.common.feign.exception;

import java.net.URI;
import java.util.Map;

public class SeatAlreadyLockedException extends RidehubApiException {
    public SeatAlreadyLockedException(String title, String detail, URI type, String errorKey, Map<String, Object> parameters) {
        super(409, title != null ? title : "Seat Already Locked", detail, type, errorKey, parameters);
    }
}
