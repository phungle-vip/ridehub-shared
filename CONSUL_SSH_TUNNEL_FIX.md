# Consul SSH Tunnel Bootstrap Override Fix

## Problem
The ConsulSSHTunnel configuration in the central library was not properly overriding the bootstrap.yml configuration in each microservice. Specifically, the `ms_route` service had hardcoded values in its bootstrap.yml that prevented the dynamic properties from being applied.

## Root Cause
1. The `ms_route/src/main/resources/config/bootstrap.yml` file had hardcoded values for Consul discovery configuration:
   - `ip-address: phungvip.io.vn`
   - `port: 8500`
   - `prefer-ip-address: true`

2. The `ConsulSSHTunnel` class was updating properties after the bootstrap configuration had already been processed, making the overrides ineffective.

## Solution

### 1. Updated Bootstrap Configuration Files
- **ms_route/src/main/resources/config/bootstrap.yml**: Changed hardcoded values to property placeholders:
  - `ip-address: ${tunnel.vps-host:phungvip.io.vn}`
  - `port: ${tunnel.tunnel-port:8500}`
  - `prefer-ip-address: false`

- **ridehub-central-config/bootstrap.yml**: Applied the same changes for consistency

### 2. Created Early Bootstrap Initializer
- **ConsulSSHTunnelInitializer.java**: New ApplicationContextInitializer that runs early in the bootstrap process to set the dynamic properties before Consul configuration is loaded
- **META-INF/spring.factories**: Registered the initializer to be automatically loaded

### 3. Refactored ConsulSSHTunnel Class
- Removed the `updateConsulProperties()` method since this is now handled by the initializer
- Simplified the class to focus only on SSH tunnel creation and service registration customization

## How It Works Now

1. **Bootstrap Phase**: The `ConsulSSHTunnelInitializer` runs early and sets the dynamic properties with highest precedence
2. **Configuration Loading**: Bootstrap.yml files use property placeholders that reference these dynamic properties
3. **Tunnel Creation**: The `ConsulSSHTunnel` class creates the actual SSH tunnel
4. **Service Registration**: The `ConsulRegistrationCustomizer` ensures correct service registration with Consul

## Benefits
- Consistent behavior across all microservices
- Dynamic properties are properly applied during bootstrap
- SSH tunnel configuration works correctly for all services
- Easier maintenance and configuration management

## Files Modified
1. `backend/ms_route/src/main/resources/config/bootstrap.yml`
2. `backend/ridehub-central-config/bootstrap.yml`
3. `backend/ridehub-central-common/src/main/java/com/ridehub/common/config/ConsulSSHTunnel.java`
4. `backend/ridehub-central-common/src/main/java/com/ridehub/common/config/ConsulSSHTunnelInitializer.java` (new)
5. `backend/ridehub-central-common/src/main/resources/META-INF/spring.factories` (new)