package com.ridehub.common.feign;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.beans.factory.annotation.Value;

import java.util.ArrayList;
import java.util.List;

/**
 * Consolidated configuration properties for Ridehub Feign functionality.
 * Combines scanning and authentication properties.
 */
@ConfigurationProperties(prefix = "ridehub.feign")
public class RidehubFeignProperties {

    // ================== Scanning Properties ==================
    private List<ScanSpec> scans = new ArrayList<>();

    public static class ScanSpec {
        private String basePackage; // e.g. com.ridehub.msbooking.client.api
        private String serviceId; // e.g. ${ridehub.services.booking:msbooking}

        public String getBasePackage() {
            return basePackage;
        }

        public void setBasePackage(String basePackage) {
            this.basePackage = basePackage;
        }

        public String getServiceId() {
            return serviceId;
        }

        public void setServiceId(String serviceId) {
            this.serviceId = serviceId;
        }
    }

    public List<ScanSpec> getScans() {
        return scans;
    }

    public void setScans(List<ScanSpec> scans) {
        this.scans = scans;
    }

    // ================== Authentication Properties ==================
    /**
     * Full token endpoint URL:
     * https://keycloak.../realms/<realm>/protocol/openid-connect/token
     */
    private String tokenUrl;
    private String clientId; // e.g. ms-booking
    private String clientSecret; // Keycloak client secret
    private String scope; // optional

    // ================== Resilience & Timeout Properties ==================
    private int connectTimeoutMillis = 2000;
    private int readTimeoutMillis = 5000;
    private boolean retryEnabled = true;
    private int retryMaxAttempts = 3;
    private long retryPeriodMillis = 100;
    private long retryMaxPeriodMillis = 1000;

    // ================== Common Properties ==================
    /**
     * Context path for services (rarely used)
     */
    @Value("${services.context-path:}")
    private String contextPath = "";

    // Getters and Setters
    public int getConnectTimeoutMillis() {
        return connectTimeoutMillis;
    }

    public void setConnectTimeoutMillis(int connectTimeoutMillis) {
        this.connectTimeoutMillis = connectTimeoutMillis;
    }

    public int getReadTimeoutMillis() {
        return readTimeoutMillis;
    }

    public void setReadTimeoutMillis(int readTimeoutMillis) {
        this.readTimeoutMillis = readTimeoutMillis;
    }

    public boolean isRetryEnabled() {
        return retryEnabled;
    }

    public void setRetryEnabled(boolean retryEnabled) {
        this.retryEnabled = retryEnabled;
    }

    public int getRetryMaxAttempts() {
        return retryMaxAttempts;
    }

    public void setRetryMaxAttempts(int retryMaxAttempts) {
        this.retryMaxAttempts = retryMaxAttempts;
    }

    public long getRetryPeriodMillis() {
        return retryPeriodMillis;
    }

    public void setRetryPeriodMillis(long retryPeriodMillis) {
        this.retryPeriodMillis = retryPeriodMillis;
    }

    public long getRetryMaxPeriodMillis() {
        return retryMaxPeriodMillis;
    }

    public void setRetryMaxPeriodMillis(long retryMaxPeriodMillis) {
        this.retryMaxPeriodMillis = retryMaxPeriodMillis;
    }
    public String getTokenUrl() {
        return tokenUrl;
    }

    public void setTokenUrl(String tokenUrl) {
        this.tokenUrl = tokenUrl;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public String getContextPath() {
        return contextPath;
    }

    public void setContextPath(String contextPath) {
        this.contextPath = contextPath;
    }

    /**
     * Get the context path with leading slash if not empty
     */
    public String getFormattedContextPath() {
        if (contextPath == null || contextPath.isBlank()) {
            return "";
        }
        return contextPath.startsWith("/") ? contextPath : "/" + contextPath;
    }
}