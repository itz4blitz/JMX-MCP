# JMX MCP Server Troubleshooting Guide

This guide helps you diagnose and resolve common issues with the JMX MCP Server.

## Quick Diagnostics

### 1. Check Server Status

```bash
# Test if server starts correctly
java -jar target/jmx-mcp-server-1.0.0.jar --spring.profiles.active=stdio

# Run comprehensive tests
python3 comprehensive-test.py
```

### 2. Verify Java Version

```bash
java -version
# Should show Java 17 or higher
```

### 3. Check Build Status

```bash
mvn clean compile
mvn test
```

## Common Issues

### Server Won't Start

**Symptoms:**
- Server exits immediately
- "ClassNotFoundException" errors
- Port binding errors

**Solutions:**

1. **Check Java Version:**
   ```bash
   java -version
   # Ensure Java 17+ is installed
   ```

2. **Verify JAR File:**
   ```bash
   ls -la target/jmx-mcp-server-*.jar
   # File should exist and be > 50MB
   ```

3. **Check for Port Conflicts:**
   ```bash
   # If using web profile
   lsof -i :8080
   netstat -an | grep 8080
   ```

4. **Review Startup Logs:**
   ```bash
   java -jar target/jmx-mcp-server-1.0.0.jar 2>&1 | tee startup.log
   ```

### Claude Desktop Integration Issues

**Symptoms:**
- No tools/resources visible in Claude
- "Server not responding" errors
- JSON-RPC communication failures

**Solutions:**

1. **Verify Configuration Path:**
   ```bash
   # macOS
   cat ~/.config/claude/mcp_servers.json
   
   # Windows
   type %APPDATA%\Claude\mcp_servers.json
   ```

2. **Check JAR Path in Configuration:**
   ```json
   {
     "mcpServers": {
       "jmx-mcp-server": {
         "command": "java",
         "args": [
           "-jar",
           "/FULL/PATH/TO/jmx-mcp-server-1.0.0.jar"
         ]
       }
     }
   }
   ```

3. **Enable Debug Mode:**
   ```json
   {
     "mcpServers": {
       "jmx-mcp-server": {
         "command": "java",
         "args": [
           "-Dlogging.level.org.jmxmcp=DEBUG",
           "-jar",
           "/path/to/jmx-mcp-server-1.0.0.jar"
         ]
       }
     }
   }
   ```

4. **Test STDIO Communication:**
   ```bash
   echo '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"test","version":"1.0"}}}' | java -Dspring.profiles.active=stdio -jar target/jmx-mcp-server-1.0.0.jar
   ```

### JMX Connection Problems

**Symptoms:**
- "Connection refused" errors
- "MBean not found" errors
- Timeout exceptions

**Solutions:**

1. **Local JMX Issues:**
   ```bash
   # Check if JMX is enabled
   jps -v | grep jmxremote
   
   # Enable JMX for target application
   java -Dcom.sun.management.jmxremote \
        -Dcom.sun.management.jmxremote.port=9999 \
        -Dcom.sun.management.jmxremote.authenticate=false \
        -Dcom.sun.management.jmxremote.ssl=false \
        -jar your-app.jar
   ```

2. **Remote JMX Issues:**
   ```bash
   # Test connectivity
   telnet remote-host 9999
   
   # Check firewall settings
   nmap -p 9999 remote-host
   ```

3. **Authentication Problems:**
   ```yaml
   # application-remote.yml
   jmx:
     mcp:
       connection:
         url: "service:jmx:rmi:///jndi/rmi://host:9999/jmxrmi"
         username: "correct-username"
         password: "correct-password"
   ```

### Memory and Performance Issues

**Symptoms:**
- OutOfMemoryError
- Slow response times
- High CPU usage

**Solutions:**

1. **Increase Heap Size:**
   ```bash
   java -Xmx2g -Xms1g -jar jmx-mcp-server-1.0.0.jar
   ```

2. **Optimize Discovery Settings:**
   ```yaml
   jmx:
     mcp:
       discovery:
         refresh-interval: 120s  # Increase interval
         max-mbeans: 500        # Limit MBean count
         include-patterns:
           - "java.lang:*"      # Only essential domains
   ```

3. **Enable GC Logging:**
   ```bash
   java -XX:+PrintGC -XX:+PrintGCDetails \
        -Xloggc:gc.log \
        -jar jmx-mcp-server-1.0.0.jar
   ```

### Tool Execution Failures

**Symptoms:**
- "Tool not found" errors
- Parameter validation failures
- Security violations

**Solutions:**

1. **Check Tool Registration:**
   ```bash
   # Send tools/list request
   echo '{"jsonrpc":"2.0","id":1,"method":"tools/list"}' | \
   java -Dspring.profiles.active=stdio -jar target/jmx-mcp-server-1.0.0.jar
   ```

2. **Validate Parameters:**
   ```json
   {
     "name": "getAttribute",
     "arguments": {
       "objectName": "java.lang:type=Memory",
       "attributeName": "HeapMemoryUsage"
     }
   }
   ```

3. **Check Security Settings:**
   ```yaml
   jmx:
     mcp:
       security:
         validate-parameters: false  # Temporarily disable for testing
         dangerous-operations: []    # Allow all operations for testing
   ```

## Debugging Techniques

### Enable Verbose Logging

```yaml
logging:
  level:
    org.jmxmcp: DEBUG
    org.springframework.ai.mcp: DEBUG
    javax.management: DEBUG
    javax.management.remote: DEBUG
```

### Capture Network Traffic

```bash
# For remote JMX debugging
tcpdump -i any -w jmx-traffic.pcap port 9999
```

### JVM Debugging

```bash
# Enable JVM debugging
java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005 \
     -jar jmx-mcp-server-1.0.0.jar
```

### MBean Browser

Use JConsole or VisualVM to inspect available MBeans:

```bash
# Connect to local JVM
jconsole

# Connect to remote JMX
jconsole service:jmx:rmi:///jndi/rmi://host:9999/jmxrmi
```

## Error Messages and Solutions

### "Failed to initialize MCP server"

**Cause:** Configuration or dependency issues

**Solution:**
```bash
# Check dependencies
mvn dependency:tree

# Verify configuration
java -Dspring.config.location=classpath:/application.yml \
     -jar target/jmx-mcp-server-1.0.0.jar
```

### "ObjectName malformed"

**Cause:** Invalid MBean ObjectName format

**Solution:**
```java
// Correct format
"java.lang:type=Memory"
"com.myapp:type=Cache,name=UserCache"

// Incorrect format
"invalid:name"
"java.lang:type="
```

### "Attribute not readable/writable"

**Cause:** Attempting to read non-readable or write non-writable attributes

**Solution:**
```bash
# Check attribute info first
curl -X POST -H "Content-Type: application/json" \
     -d '{"name":"getMBeanInfo","arguments":{"objectName":"java.lang:type=Memory"}}' \
     http://localhost:8080/tools/call
```

### "Connection timeout"

**Cause:** Network or JMX server issues

**Solution:**
```yaml
jmx:
  mcp:
    connection:
      timeout: 60s  # Increase timeout
      properties:
        "jmx.remote.x.request.waiting.timeout": "30000"
```

## Performance Monitoring

### Monitor Server Health

```bash
# Check memory usage
jstat -gc <pid>

# Monitor threads
jstack <pid>

# Check heap dump
jmap -dump:format=b,file=heap.hprof <pid>
```

### Application Metrics

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,metrics,info
  metrics:
    export:
      prometheus:
        enabled: true
```

## Getting Help

### Collect Diagnostic Information

```bash
#!/bin/bash
# diagnostic-info.sh

echo "=== System Information ==="
uname -a
java -version
mvn -version

echo "=== JMX MCP Server Version ==="
java -jar target/jmx-mcp-server-1.0.0.jar --version

echo "=== Configuration ==="
cat src/main/resources/application*.yml

echo "=== Recent Logs ==="
tail -100 jmx-mcp-server.log

echo "=== Process Information ==="
ps aux | grep jmx-mcp-server

echo "=== Network Connections ==="
netstat -an | grep -E "(8080|9999)"
```

### Create Minimal Reproduction

1. **Isolate the Issue:**
   ```bash
   # Test with minimal configuration
   java -Dspring.profiles.active=stdio \
        -Dlogging.level.org.jmxmcp=DEBUG \
        -jar target/jmx-mcp-server-1.0.0.jar
   ```

2. **Document Steps:**
   - Exact commands used
   - Configuration files
   - Error messages
   - Expected vs actual behavior

3. **Provide Environment Details:**
   - Operating system
   - Java version
   - JMX MCP Server version
   - Target application details

### Community Support

- **GitHub Issues:** [Report bugs and request features](https://github.com/itz4blitz/jmx-mcp-server/issues)
- **GitHub Discussions:** [Ask questions and share ideas](https://github.com/itz4blitz/jmx-mcp-server/discussions)
- **Documentation:** [Check the wiki](https://github.com/itz4blitz/jmx-mcp-server/wiki)

### Professional Support

For enterprise support and consulting:
- Email: [itzdarkblitz@protonmail.com](mailto:itzdarkblitz@protonmail.com)
- Include diagnostic information and business requirements
