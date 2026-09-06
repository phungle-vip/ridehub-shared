package com.ridehub.common.feign;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import feign.Logger;
import feign.RequestInterceptor;
import feign.codec.Decoder;
import feign.codec.Encoder;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.Resource;
import org.springframework.core.type.AnnotationMetadata;


/**
 * Consolidated auto-configuration for Ridehub Feign clients.
 * Combines functionality from FeignCommonAutoConfiguration and EnableRidehubFeign.
 */
@AutoConfiguration
@ConditionalOnClass({ feign.Feign.class, LoadBalancerClient.class })
@EnableConfigurationProperties(RidehubFeignProperties.class)
@Import({ RidehubFeignAutoConfiguration.FeignClientRegistrar.class })
public class RidehubFeignAutoConfiguration {

    // ================== Jackson Configuration ==================
    @Bean
    @ConditionalOnMissingBean(name = "feignObjectMapper")
    public ObjectMapper feignObjectMapper() {
        var mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }

    @Bean
    @ConditionalOnMissingBean(name = "feignEncoder")
    public Encoder feignEncoder(@Qualifier("feignObjectMapper") ObjectMapper om) {
        return new JacksonEncoder(om);
    }

    @Bean
    @ConditionalOnMissingBean(name = "feignDecoder")
    public Decoder feignDecoder(@Qualifier("feignObjectMapper") ObjectMapper om) {
        return new JacksonDecoder(om);
    }

    // ================== Authentication Configuration ==================
    @Bean
    @ConditionalOnMissingBean(name = "authRequestInterceptor")
    public RequestInterceptor authRequestInterceptor(RidehubFeignProperties props) {
        RidehubFeignAuthManager authManager = new RidehubFeignAuthManager(
                props.getTokenUrl(), props.getClientId(), props.getClientSecret(), props.getScope());
        return authManager.createAuthInterceptor();
    }

    @Bean
    @ConditionalOnMissingBean
    public Logger.Level feignLoggerLevel() {
        return Logger.Level.FULL;
    }



    // ================== AUTOMATIC FEIGN CLIENT REGISTRATION ==================

    /**
     * Registrar that automatically scans for Feign client interfaces and registers
     * them as beans using the consolidated factory classes.
     */
    public static class FeignClientRegistrar implements ImportBeanDefinitionRegistrar {

        private static final String ROOT_PACKAGE = "com.ridehub.feign";

        @Override
        public void registerBeanDefinitions(
                AnnotationMetadata importingClassMetadata,
                BeanDefinitionRegistry registry) {

            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            String basePath = ROOT_PACKAGE.replace('.', '/');
            String pattern = "classpath*:" + basePath + "/**/*.class";

            try {
                Resource[] resources = resolver.getResources(pattern);

                for (Resource resource : resources) {
                    String className = getClassName(resource, basePath);
                    if (className == null) {
                        continue;
                    }

                    Class<?> clazz = Class.forName(className);

                    // Only scan interface Feign API classes (ending with Api)
                    if (!clazz.isInterface()) {
                        continue;
                    }
                    if (!clazz.getSimpleName().endsWith("Api")) {
                        continue;
                    }

                    // Extract serviceId from package name:
                    // com.ridehub.feign.<serviceId>.client.api.XApi
                    String serviceId = extractServiceId(clazz);
                    if (serviceId == null) {
                        continue;
                    }

                    registerFeignClientBean(registry, clazz, serviceId);
                }

            } catch (Exception ignored) {
                // Silently ignore scanning errors
            }
        }

        private String getClassName(Resource resource, String basePath) {
            try {
                String uri = resource.getURI().toString();
                int idx = uri.indexOf(basePath);
                if (idx == -1) {
                    return null;
                }

                String classPath = uri.substring(idx)
                        .replace('/', '.')
                        .replace(".class", "");

                return classPath;
            } catch (Exception e) {
                return null;
            }
        }

        private String extractServiceId(Class<?> clazz) {
            String pkg = clazz.getPackage().getName();
            // expected: com.ridehub.feign.<serviceId>.client.api
            String[] parts = pkg.split("\\.");
            for (int i = 0; i < parts.length; i++) {
                if (parts[i].equals("feign") && i + 1 < parts.length) {
                    return parts[i + 1]; // return <serviceId>
                }
            }
            return null;
        }

        private void registerFeignClientBean(
                BeanDefinitionRegistry registry,
                Class<?> clientInterface,
                String serviceId) {

            String beanName = Character.toLowerCase(clientInterface.getSimpleName().charAt(0))
                    + clientInterface.getSimpleName().substring(1);

            if (registry.containsBeanDefinition(beanName)) {
                return;
            }

            BeanDefinition bd = BeanDefinitionBuilder
                    .genericBeanDefinition(RidehubFeignFactory.LazyProxyFactoryBean.class)
                    .addConstructorArgValue(clientInterface)
                    .addConstructorArgValue(serviceId)
                    .setLazyInit(true)
                    .getBeanDefinition();

            registry.registerBeanDefinition(beanName, bd);
        }
    }
}