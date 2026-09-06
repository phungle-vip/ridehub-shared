package com.ridehub.common.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.consul.ConditionalOnConsulEnabled;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

/**
 * Auto-configuration for Consul SSH Tunnel functionality.
 * This class automatically configures the SSH tunnel when the required dependencies are present
 * and the application is running in the "dev" profile.
 */
@AutoConfiguration
@ConditionalOnConsulEnabled
@ConditionalOnProperty(value = "tunnel.enabled", havingValue = "true", matchIfMissing = false)
@EnableConfigurationProperties(ConsulSSHTunnelProperties.class)
@Import(ConsulSSHTunnel.class)
public class ConsulSSHTunnelAutoConfiguration {
    // Auto-configuration is handled through @Import and @EnableConfigurationProperties
}