package com.ridehub.common.feign;

import feign.Feign;
import feign.Logger;
import feign.RequestInterceptor;
import feign.codec.Decoder;
import feign.codec.Encoder;
import feign.slf4j.Slf4jLogger;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.cloud.openfeign.FeignClientBuilder;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.ServiceInstance;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

/**
 * Consolidated factory for creating Feign client instances.
 * Combines functionality from LazyFeignFactory, RidehubFeignFactoryBean,
 * FeignClientFactoryBean, and RidehubOpenApiClassFactoryBean.
 */
@Component
public class RidehubFeignFactory {

    private final ApplicationContext ctx;
    private final Encoder encoder;
    private final Decoder decoder;
    private final RequestInterceptor auth;
    private final Logger.Level level;

    @Autowired
    private LoadBalancerClient loadBalancerClient;

    @Autowired(required = false)
    private feign.codec.ErrorDecoder errorDecoder;

    @Autowired(required = false)
    private feign.Request.Options requestOptions;

    @Autowired(required = false)
    private feign.Retryer retryer;

    @Autowired(required = false)
    @org.springframework.beans.factory.annotation.Qualifier("feignTraceInterceptor")
    private RequestInterceptor traceInterceptor;

    public RidehubFeignFactory(
            ApplicationContext ctx,
            Encoder encoder,
            Decoder decoder,
            RequestInterceptor authRequestInterceptor,
            Logger.Level level) {
        this.ctx = ctx;
        this.encoder = encoder;
        this.decoder = decoder;
        this.auth = authRequestInterceptor;
        this.level = level;
    }

    /**
     * Create a Feign client for a given serviceId and API type
     */
    public <T> T create(String serviceId, Class<T> apiType) {
        return new FeignClientBuilder(ctx)
                .forType(apiType, serviceId)
                .customize(builder -> {
                    builder.encoder(encoder);
                    builder.decoder(decoder);
                    if (errorDecoder != null) {
                        builder.errorDecoder(errorDecoder);
                    }
                    if (requestOptions != null) {
                        builder.options(requestOptions);
                    }
                    if (retryer != null) {
                        builder.retryer(retryer);
                    }
                    builder.requestInterceptor(auth);
                    if (traceInterceptor != null) {
                        builder.requestInterceptor(traceInterceptor);
                    }
                    builder.logLevel(level);
                })
                .build();
    }

    /**
     * Resolves a ServiceInstance using LoadBalancerClient, trying multiple candidate
     * naming conventions (e.g. msroute, ms_route, ms-route, msroutedev, ms_routedev)
     */
    public static ServiceInstance resolveServiceInstance(LoadBalancerClient loadBalancerClient, String serviceId) {
        if (loadBalancerClient == null || serviceId == null || serviceId.isBlank()) {
            return null;
        }

        // 1. Exact match
        ServiceInstance instance = loadBalancerClient.choose(serviceId);
        if (instance != null) {
            return instance;
        }

        // 2. Build candidate variations
        List<String> candidates = new ArrayList<>();

        // e.g. msroute -> ms_route, ms-route
        if (serviceId.startsWith("ms") && serviceId.length() > 2
                && serviceId.charAt(2) != '_' && serviceId.charAt(2) != '-') {
            String suffix = serviceId.substring(2);
            candidates.add("ms_" + suffix);
            candidates.add("ms-" + suffix);
        }
        // e.g. ms_route -> msroute, ms-route
        if (serviceId.contains("_")) {
            candidates.add(serviceId.replace("_", ""));
            candidates.add(serviceId.replace("_", "-"));
        }
        // e.g. ms-route -> msroute, ms_route
        if (serviceId.contains("-")) {
            candidates.add(serviceId.replace("-", ""));
            candidates.add(serviceId.replace("-", "_"));
        }

        // Also check dev suffix variations (used in local ConsulSSHTunnel dev profile)
        int size = candidates.size();
        for (int i = 0; i < size; i++) {
            candidates.add(candidates.get(i) + "dev");
        }
        candidates.add(serviceId + "dev");

        for (String candidate : candidates) {
            instance = loadBalancerClient.choose(candidate);
            if (instance != null) {
                LoggerFactory.getLogger(RidehubFeignFactory.class)
                        .info("Resolved service [{}] via candidate [{}] -> {}", serviceId, candidate, instance.getUri());
                return instance;
            }
        }

        return null;
    }

    /**
     * FactoryBean for interface-based Feign clients (lazy loading)
     */
    public static class InterfaceFactoryBean<T> implements FactoryBean<T> {
        private final Class<T> apiType;
        private final String serviceId;
        private RidehubFeignFactory factory;

        public InterfaceFactoryBean(Class<T> apiType, String serviceId) {
            this.apiType = apiType;
            this.serviceId = serviceId;
        }

        @Autowired
        public void setFactory(RidehubFeignFactory factory) {
            this.factory = factory;
        }

        @Override
        public T getObject() {
            return factory.create(serviceId, apiType);
        }

        @Override
        public Class<?> getObjectType() {
            return apiType;
        }

        @Override
        public boolean isSingleton() {
            return true;
        }
    }

    /**
     * FactoryBean for OpenAPI concrete classes
     */
    public static class OpenApiFactoryBean<T> implements FactoryBean<T> {
        private final Class<T> apiClass;
        private final String serviceId;
        private LoadBalancerClient loadBalancerClient;

        public OpenApiFactoryBean(Class<T> apiClass, String serviceId) {
            this.apiClass = apiClass;
            this.serviceId = serviceId;
        }

        @Autowired
        public void setLoadBalancerClient(LoadBalancerClient loadBalancerClient) {
            this.loadBalancerClient = loadBalancerClient;
        }

        @Override
        public T getObject() throws Exception {
            var instance = resolveServiceInstance(loadBalancerClient, serviceId);
            if (instance == null) {
                throw new IllegalStateException("Service [" + serviceId + "] not found via discovery (checked variations)");
            }
            String base = instance.getUri().toString().replaceAll("/+$", "");

            // Find the generated ApiClient in same package
            String apiClientClassName = apiClass.getPackageName().replace(".api", "") + ".ApiClient";
            Class<?> apiClientClass = Class.forName(apiClientClassName);
            Object apiClient = apiClientClass.getConstructor().newInstance();
            apiClientClass.getMethod("setBasePath", String.class).invoke(apiClient, base);

            return apiClass.getConstructor(apiClientClass).newInstance(apiClient);
        }

        @Override
        public Class<?> getObjectType() {
            return apiClass;
        }

        @Override
        public boolean isSingleton() {
            return true;
        }
    }

    /**
     * FactoryBean for lazy-loading Feign clients with proxy
     */
    public static class LazyProxyFactoryBean<T> implements FactoryBean<T> {
        private final Class<T> clientInterface;
        private final String serviceId;
        private LoadBalancerClient loadBalancerClient;
        private RequestInterceptor authRequestInterceptor;
        private Encoder feignEncoder;
        private Decoder feignDecoder;
        private String contextPath = "";

        @Autowired(required = false)
        private feign.codec.ErrorDecoder errorDecoder;

        @Autowired(required = false)
        private feign.Request.Options requestOptions;

        @Autowired(required = false)
        private feign.Retryer retryer;

        @Autowired(required = false)
        @org.springframework.beans.factory.annotation.Qualifier("feignTraceInterceptor")
        private RequestInterceptor traceInterceptor;

        public LazyProxyFactoryBean(Class<T> clientInterface, String serviceId) {
            this.clientInterface = clientInterface;
            this.serviceId = serviceId;
        }

        @Autowired
        public void setLoadBalancerClient(LoadBalancerClient loadBalancerClient) {
            this.loadBalancerClient = loadBalancerClient;
        }

        @Autowired
        public void setAuthRequestInterceptor(RequestInterceptor authRequestInterceptor) {
            this.authRequestInterceptor = authRequestInterceptor;
        }

        @Autowired
        public void setFeignEncoder(Encoder feignEncoder) {
            this.feignEncoder = feignEncoder;
        }

        @Autowired
        public void setFeignDecoder(Decoder feignDecoder) {
            this.feignDecoder = feignDecoder;
        }

        public void setContextPath(String contextPath) {
            this.contextPath = contextPath;
        }

        @Override
        public T getObject() throws Exception {
            return createLazyProxy();
        }

        @Override
        public Class<?> getObjectType() {
            return clientInterface;
        }

        @Override
        public boolean isSingleton() {
            return true;
        }

        @SuppressWarnings("unchecked")
        private T createLazyProxy() {
            return (T) Proxy.newProxyInstance(
                    clientInterface.getClassLoader(),
                    new Class<?>[]{clientInterface},
                    new LazyFeignHandler<>(serviceId, clientInterface, authRequestInterceptor, traceInterceptor,
                            feignEncoder, feignDecoder, errorDecoder, requestOptions, retryer, loadBalancerClient, contextPath));
        }

        private static class LazyFeignHandler<U> implements InvocationHandler {
            private final String serviceId;
            private final Class<U> clazz;
            private final RequestInterceptor auth;
            private final RequestInterceptor traceInterceptor;
            private final Encoder encoder;
            private final Decoder decoder;
            private final feign.codec.ErrorDecoder errorDecoder;
            private final feign.Request.Options requestOptions;
            private final feign.Retryer retryer;
            private final LoadBalancerClient loadBalancerClient;
            private final String contextPath;
            private volatile U feignClient;

            LazyFeignHandler(String serviceId, Class<U> clazz, RequestInterceptor auth, RequestInterceptor traceInterceptor,
                            Encoder encoder, Decoder decoder, feign.codec.ErrorDecoder errorDecoder,
                            feign.Request.Options requestOptions, feign.Retryer retryer,
                            LoadBalancerClient loadBalancerClient, String contextPath) {
                this.serviceId = serviceId;
                this.clazz = clazz;
                this.auth = auth;
                this.traceInterceptor = traceInterceptor;
                this.encoder = encoder;
                this.decoder = decoder;
                this.errorDecoder = errorDecoder;
                this.requestOptions = requestOptions;
                this.retryer = retryer;
                this.loadBalancerClient = loadBalancerClient;
                this.contextPath = contextPath;
            }

            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                switch (method.getName()) {
                    case "hashCode":
                        return System.identityHashCode(proxy);
                    case "toString":
                        return "LazyFeignProxy(" + clazz.getSimpleName() + "->" + serviceId + ")";
                    case "equals":
                        return proxy == args[0];
                }

                if (feignClient == null) {
                    synchronized (this) {
                        if (feignClient == null) {
                            var instance = resolveServiceInstance(loadBalancerClient, serviceId);
                            if (instance == null) {
                                throw new IllegalStateException("Service [" + serviceId + "] not found via discovery (checked variations)");
                            }
                            String baseUrl = instance.getUri().toString();

                            // Add context path if configured
                            if (contextPath != null && !contextPath.isBlank()) {
                                // Ensure only one slash
                                baseUrl = baseUrl.replaceAll("/+$", "")
                                        + (contextPath.startsWith("/") ? contextPath : "/" + contextPath);
                            }

                            feign.Feign.Builder builder = Feign.builder()
                                    .encoder(encoder)
                                    .decoder(decoder)
                                    .requestInterceptor(auth)
                                    .logger(new Slf4jLogger(clazz))
                                    .logLevel(Logger.Level.FULL);

                            if (errorDecoder != null) {
                                builder.errorDecoder(errorDecoder);
                            }
                            if (requestOptions != null) {
                                builder.options(requestOptions);
                            }
                            if (retryer != null) {
                                builder.retryer(retryer);
                            }
                            if (traceInterceptor != null) {
                                builder.requestInterceptor(traceInterceptor);
                            }

                            feignClient = builder.target(clazz, baseUrl);
                        }
                    }
                }
                return method.invoke(feignClient, args);
            }
        }
    }
}