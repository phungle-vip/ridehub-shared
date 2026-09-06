# RideHub Shared Library

Thư viện dùng chung (Shared Runtime Library) cho hệ sinh thái microservices RideHub.

## Trách nhiệm (Ownership)
- Cung cấp các OpenFeign client được cấu hình sẵn cho các microservice (`ms_booking`, `ms_route`, `ms_user`, `ms_promotion`).
- Tích hợp bảo mật: xác thực Keycloak, token handler, và tự động cấu hình interceptor cho Feign.
- Tích hợp hạ tầng: Consul Service Discovery, Redis caching (Redisson), và Kafka event producer/consumer helpers.
- **Phụ thuộc vào `ridehub-contract`**: Sử dụng các contract/schema định nghĩa từ `ridehub-contract` để đảm bảo tính nhất quán dữ liệu giữa các dịch vụ.
- **Không** chứa logic nghiệp vụ đặc thù hay bí mật môi trường production.

## Cấu trúc thư mục

```
ridehub-shared/
├── src/main/
│   ├── java/com/ridehub/
│   │   ├── feign/              # Generated OpenFeign clients & API invokers
│   │   └── avro/               # Avro envelope & event handlers
│   └── resources/              # Auto-configuration & properties template
├── scripts/                    # Scripts tải OpenAPI specs và tạo client
├── .github/workflows/          # CI/CD publish package lên GitHub Packages
└── pom.xml                     # Maven project descriptor
```

## Hướng dẫn sử dụng

### 1. Build và cài đặt cục bộ
```bash
./mvnw clean install -DskipTests
```

### 2. Sử dụng trong microservice
Khai báo dependency trong `pom.xml` của microservice:
```xml
<dependency>
  <groupId>com.ridehub.clients</groupId>
  <artifactId>client-open-feign-avro</artifactId>
  <version>0.1.0</version>
</dependency>
```

### 3. Phân phối (Publishing)
Thư viện được cấu hình xuất bản tự động lên GitHub Packages (`phungle-vip/ridehub-shared`) khi tạo git tag theo định dạng `v*.*.*`.
