package com.ridehub.common.config;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.consul.serviceregistry.ConsulRegistrationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;

/**
 * Configuration class for creating SSH tunnel to VPS for development
 * environment.
 * This class creates an SSH tunnel and updates Consul service registration
 * properties
 * to enable service discovery in development environments.
 */
@Configuration
@Profile("dev") // Only activate this configuration in dev profile
public class ConsulSSHTunnel {
    private static final Logger log = LoggerFactory.getLogger(ConsulSSHTunnel.class);
    private Session session;

    @Autowired
    private ConsulSSHTunnelProperties tunnelProperties;

    @Value("${spring.application.name}")
    private String appName;

    @Autowired
    private ConfigurableEnvironment environment;

    @PostConstruct
    public void init() {
        // Run tunnel creation in separate thread to avoid blocking Spring startup
        new Thread(this::createTunnel).start();
    }

    private void createTunnel() {
        try {
            if (tunnelProperties.getVpsHost() == null || tunnelProperties.getVpsHost().isBlank()
                    || tunnelProperties.getVpsUser() == null || tunnelProperties.getVpsUser().isBlank()) {
                log.info("Consul SSH tunnel skipped: tunnel.vps-host or tunnel.vps-user is not configured");
                return;
            }

            Thread.sleep(2000); // Wait for app to start
            JSch jsch = new JSch();
            session = jsch.getSession(
                    tunnelProperties.getVpsUser(),
                    tunnelProperties.getVpsHost(),
                    22);
            session.setPassword(tunnelProperties.getVpsPassword());
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect(10000);
            session.setPortForwardingR(
                    tunnelProperties.getTunnelPort(),
                    "localhost",
                    tunnelProperties.getLocalPort());
            log.info("SSH tunnel created: {}:{} -> localhost:{}",
                    tunnelProperties.getVpsHost(),
                    tunnelProperties.getTunnelPort(),
                    tunnelProperties.getLocalPort());
        } catch (Exception e) {
            log.error("SSH tunnel failed: {}", e.getMessage());
        }
    }

    @PreDestroy
    public void destroy() {
        if (session != null && session.isConnected()) {
            session.disconnect();
            log.info("SSH tunnel closed");
        }
    }

    @Bean
    @Profile("dev") // Only create this bean in dev profile
    ConsulRegistrationCustomizer portCustomizer() {
        return registration -> {
            String vpsHost = tunnelProperties.getVpsHost();
            if (vpsHost == null || vpsHost.isBlank()) {
                return; // Do not alter registration if tunnel is not active
            }

            // Use configured values or defaults
            String serviceId = tunnelProperties.getServiceId() != null ? tunnelProperties.getServiceId() : appName;
            String serviceName = tunnelProperties.getServiceName() != null ? tunnelProperties.getServiceName()
                    : appName;
            int tunnelPort = tunnelProperties.getTunnelPort();
            String devSuffix = tunnelProperties.getDevSuffix();

            // Set service registration properties
            registration.getService().setAddress(vpsHost);
            registration.getService().setId(serviceId + devSuffix.toLowerCase());
            registration.getService().setName(serviceName + devSuffix);
            registration.getService().setPort(tunnelPort);

            log.info("Consul registration customized: address={}, name={}, port={}",
                    vpsHost, serviceName + devSuffix, tunnelPort);
        };
    }
}