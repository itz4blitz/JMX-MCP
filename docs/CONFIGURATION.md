# JMX MCP Server Configuration Guide

This guide covers advanced configuration options for the JMX MCP Server.

## Configuration Files

The server uses Spring Boot's configuration system with support for multiple profiles and formats.

### Main Configuration File

**Location:** `src/main/resources/application.yml`

```yaml
spring:
  application:
    name: jmx-mcp-server
  ai:
    mcp:
      server:
        enabled: true
        name: "JMX MCP Server"
        version: "1.0.0"
        type: SYNC

jmx:
  mcp:
    connection:
      type: LOCAL
      timeout: 30s
    discovery:
      refresh-interval: 30s
      discover-on-startup: true
    tools:
      enabled: true
    resources:
      enabled: true
    security:
      validate-parameters: true
```

## Profiles

### Default Profile

Standard configuration for development and testing.

### STDIO Profile

**File:** `application-stdio.yml`

Optimized for Claude Desktop integration:

```yaml
spring:
  main:
    banner-mode: off
    log-startup-info: false

logging:
  level:
    root: OFF
    org.jmxmcp: ERROR

jmx:
  mcp:
    discovery:
      refresh-interval: 60s  # Longer interval for stability
```

**Usage:**
```bash
java -Dspring.profiles.active=stdio -jar jmx-mcp-server.jar
```

### Remote Profile

**File:** `application-remote.yml`

For connecting to remote JMX servers:

```yaml
jmx:
  mcp:
    connection:
      type: REMOTE
      url: "service:jmx:rmi:///jndi/rmi://localhost:9999/jmxrmi"
      username: "admin"
      password: "password"
      timeout: 30s
      properties:
        "jmx.remote.x.request.waiting.timeout": "10000"
```

### Docker Profile

**File:** `application-docker.yml`

Optimized for containerized deployments:

```yaml
server:
  port: 8080

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics

jmx:
  mcp:
    discovery:
      refresh-interval: 120s  # Reduced frequency for containers
```

## JMX Connection Configuration

### Local Connection (Default)

Connects to the same JVM process:

```yaml
jmx:
  mcp:
    connection:
      type: LOCAL
      timeout: 30s
```

### Remote Connection

Connect to external JMX servers:

```yaml
jmx:
  mcp:
    connection:
      type: REMOTE
      url: "service:jmx:rmi:///jndi/rmi://remote-host:9999/jmxrmi"
      username: "jmx-user"
      password: "jmx-password"
      timeout: 30s
      properties:
        "jmx.remote.x.request.waiting.timeout": "10000"
        "jmx.remote.x.notification.fetch.timeout": "10000"
```

### Multi-Connection Setup

**File:** `application-multi-connection.yml`

Connect to multiple JMX servers:

```yaml
jmx:
  mcp:
    connections:
      - name: "local"
        type: LOCAL
        timeout: 30s
      - name: "app-server"
        type: REMOTE
        url: "service:jmx:rmi:///jndi/rmi://app-server:9999/jmxrmi"
        username: "monitor"
        password: "secret"
      - name: "database"
        type: REMOTE
        url: "service:jmx:rmi:///jndi/rmi://db-server:9998/jmxrmi"
```

## MBean Discovery Configuration

### Include/Exclude Patterns

Control which MBeans are discovered:

```yaml
jmx:
  mcp:
    discovery:
      include-patterns:
        - "java.lang:*"           # JVM MBeans
        - "com.myapp:*"           # Application MBeans
        - "org.apache.catalina:*" # Tomcat MBeans
      exclude-patterns:
        - "java.util.logging:*"
        - "com.sun.management:*"
        - "*:type=RequestProcessor,*"
      refresh-interval: 30s
      discover-on-startup: true
```

### Performance Tuning

```yaml
jmx:
  mcp:
    discovery:
      refresh-interval: 60s      # How often to refresh MBean list
      discover-on-startup: true  # Discover MBeans at startup
      max-mbeans: 1000          # Maximum MBeans to track
      batch-size: 50            # Batch size for discovery operations
```

## Tools Configuration

### Enable/Disable Tools

```yaml
jmx:
  mcp:
    tools:
      enabled: true
      prefix: "jmx"              # Tool name prefix
      max-parameters: 10         # Maximum parameters per tool
      exclude-operations:
        - "addNotificationListener"
        - "removeNotificationListener"
        - "dumpAllThreads"
```

### Tool-Specific Settings

```yaml
jmx:
  mcp:
    tools:
      listMBeans:
        enabled: true
        max-results: 500
      getAttribute:
        enabled: true
        cache-duration: 5s
      setAttribute:
        enabled: true
        validate-types: true
```

## Resources Configuration

### Resource Exposure

```yaml
jmx:
  mcp:
    resources:
      enabled: true
      base-uri: "jmx://"
      include-read-only: true
      include-write-only: false
      exclude-attributes:
        - "ObjectName"
        - "Verbose"
      max-resources: 2000
```

### Resource Filtering

```yaml
jmx:
  mcp:
    resources:
      filters:
        - domain: "java.lang"
          include-attributes: ["HeapMemoryUsage", "NonHeapMemoryUsage"]
        - domain: "com.myapp"
          exclude-attributes: ["InternalState"]
```

## Security Configuration

### Basic Security

```yaml
jmx:
  mcp:
    security:
      validate-parameters: true
      dangerous-operations:
        - "shutdown"
        - "restart"
        - "stop"
        - "destroy"
        - "gc"
        - "dumpHeap"
        - "resetStatistics"
      log-operations: true
```

### Advanced Security

```yaml
jmx:
  mcp:
    security:
      enabled: true
      allowed-domains:
        - "java.lang"
        - "java.nio"
        - "com.myapp"
      blocked-domains:
        - "com.sun.management"
      allowed-operations:
        - "getAttribute"
        - "setAttribute"
      blocked-operations:
        - "invoke"
      audit:
        enabled: true
        log-file: "jmx-audit.log"
```

## Logging Configuration

### Standard Logging

**File:** `logback-spring.xml`

```xml
<configuration>
    <springProfile name="!stdio">
        <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
            <encoder>
                <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
            </encoder>
        </appender>
        
        <logger name="org.jmxmcp" level="INFO"/>
        <logger name="org.springframework.ai.mcp" level="INFO"/>
        
        <root level="INFO">
            <appender-ref ref="CONSOLE"/>
        </root>
    </springProfile>
    
    <springProfile name="stdio">
        <root level="OFF"/>
    </springProfile>
</configuration>
```

### Application-Specific Logging

```yaml
logging:
  level:
    org.jmxmcp: DEBUG
    org.springframework.ai.mcp: INFO
    javax.management: WARN
    javax.management.remote: ERROR
  file:
    name: jmx-mcp-server.log
  pattern:
    file: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"
```

## Environment Variables

Override configuration using environment variables:

```bash
# JMX Connection
export JMX_MCP_CONNECTION_TYPE=REMOTE
export JMX_MCP_CONNECTION_URL="service:jmx:rmi:///jndi/rmi://localhost:9999/jmxrmi"
export JMX_MCP_CONNECTION_USERNAME=admin
export JMX_MCP_CONNECTION_PASSWORD=secret

# Discovery
export JMX_MCP_DISCOVERY_REFRESH_INTERVAL=60s
export JMX_MCP_DISCOVERY_DISCOVER_ON_STARTUP=true

# Security
export JMX_MCP_SECURITY_VALIDATE_PARAMETERS=true
export JMX_MCP_SECURITY_LOG_OPERATIONS=true
```

## Docker Configuration

### Docker Compose

```yaml
version: '3.8'
services:
  jmx-mcp-server:
    image: jmx-mcp-server:latest
    environment:
      - SPRING_PROFILES_ACTIVE=docker
      - JMX_MCP_CONNECTION_TYPE=REMOTE
      - JMX_MCP_CONNECTION_URL=service:jmx:rmi:///jndi/rmi://app:9999/jmxrmi
    volumes:
      - ./config:/app/config:ro
      - ./logs:/app/logs
    networks:
      - monitoring
```

### Kubernetes ConfigMap

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: jmx-mcp-config
data:
  application.yml: |
    jmx:
      mcp:
        connection:
          type: REMOTE
          url: "service:jmx:rmi:///jndi/rmi://app-service:9999/jmxrmi"
        discovery:
          refresh-interval: 120s
        security:
          validate-parameters: true
```

## Performance Tuning

### Memory Settings

```bash
java -Xmx1g -Xms512m \
     -XX:+UseG1GC \
     -XX:MaxGCPauseMillis=200 \
     -jar jmx-mcp-server.jar
```

### JVM Options for Production

```bash
java -server \
     -Xmx2g -Xms1g \
     -XX:+UseG1GC \
     -XX:+UseStringDeduplication \
     -XX:+OptimizeStringConcat \
     -Djava.awt.headless=true \
     -Dspring.profiles.active=production \
     -jar jmx-mcp-server.jar
```

## Troubleshooting Configuration

### Enable Debug Logging

```yaml
logging:
  level:
    org.jmxmcp: DEBUG
    org.springframework.ai.mcp: DEBUG
    javax.management: DEBUG
```

### Configuration Validation

```bash
# Validate configuration
java -Dspring.profiles.active=validation \
     -jar jmx-mcp-server.jar \
     --spring.config.location=classpath:/application.yml,file:./custom-config.yml
```

### Health Checks

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,configprops,env
  endpoint:
    health:
      show-details: always
```
