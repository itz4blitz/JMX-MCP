# JMX MCP Server API Documentation

This document provides detailed API reference for the JMX MCP Server tools and resources.

## Overview

The JMX MCP Server exposes JMX functionality through the Model Context Protocol (MCP), providing 6 tools and dynamic resources for AI assistants to interact with Java applications.

## Tools

### 1. listMBeans

Lists all discovered MBeans with optional domain filtering.

**Parameters:**
- `domain` (optional): Filter MBeans by domain name (e.g., "java.lang")

**Example Usage:**
```json
{
  "name": "listMBeans",
  "arguments": {
    "domain": "java.lang"
  }
}
```

**Response:**
```json
{
  "content": [
    {
      "type": "text",
      "text": "Found 15 MBeans in domain 'java.lang':\n- java.lang:type=Memory\n- java.lang:type=Runtime\n..."
    }
  ]
}
```

### 2. getMBeanInfo

Gets detailed information about a specific MBean including attributes, operations, and metadata.

**Parameters:**
- `objectName` (required): The ObjectName of the MBean (e.g., "java.lang:type=Memory")

**Example Usage:**
```json
{
  "name": "getMBeanInfo",
  "arguments": {
    "objectName": "java.lang:type=Memory"
  }
}
```

**Response:**
```json
{
  "content": [
    {
      "type": "text", 
      "text": "MBean: java.lang:type=Memory\nClass: sun.management.MemoryImpl\n\nAttributes:\n- HeapMemoryUsage (CompositeData, readable)\n- NonHeapMemoryUsage (CompositeData, readable)\n..."
    }
  ]
}
```

### 3. getAttribute

Reads the value of an MBean attribute.

**Parameters:**
- `objectName` (required): The ObjectName of the MBean
- `attributeName` (required): Name of the attribute to read

**Example Usage:**
```json
{
  "name": "getAttribute",
  "arguments": {
    "objectName": "java.lang:type=Memory",
    "attributeName": "HeapMemoryUsage"
  }
}
```

**Response:**
```json
{
  "content": [
    {
      "type": "text",
      "text": "HeapMemoryUsage = {\n  committed: 536870912,\n  init: 268435456,\n  max: 8589934592,\n  used: 245760000\n}"
    }
  ]
}
```

### 4. setAttribute

Sets the value of a writable MBean attribute.

**Parameters:**
- `objectName` (required): The ObjectName of the MBean
- `attributeName` (required): Name of the attribute to set
- `value` (required): New value for the attribute
- `type` (optional): Java type of the value (auto-detected if not provided)

**Example Usage:**
```json
{
  "name": "setAttribute",
  "arguments": {
    "objectName": "java.util.logging:type=Logging",
    "attributeName": "LoggerLevel",
    "value": "DEBUG",
    "type": "java.lang.String"
  }
}
```

**Response:**
```json
{
  "content": [
    {
      "type": "text",
      "text": "Successfully set LoggerLevel = DEBUG"
    }
  ]
}
```

### 5. listDomains

Lists all available MBean domains in the JMX server.

**Parameters:** None

**Example Usage:**
```json
{
  "name": "listDomains"
}
```

**Response:**
```json
{
  "content": [
    {
      "type": "text",
      "text": "Available MBean domains (5):\n- java.lang\n- java.nio\n- java.util.logging\n- com.sun.management\n- JMImplementation"
    }
  ]
}
```

### 6. getConnectionInfo

Gets JMX connection status and statistics.

**Parameters:** None

**Example Usage:**
```json
{
  "name": "getConnectionInfo"
}
```

**Response:**
```json
{
  "content": [
    {
      "type": "text",
      "text": "JMX Connection Status:\n- Status: Connected\n- Type: Local\n- MBean Count: 224\n- Domain Count: 5\n- Connection Time: 2025-01-15T10:30:45Z"
    }
  ]
}
```

## Resources

All JMX attributes are automatically exposed as MCP resources with the URI format:

```
jmx://domain:type=Type,name=Name/attributes/AttributeName
```

### Resource Examples

| Resource URI | Description |
|--------------|-------------|
| `jmx://java.lang:type=Memory/attributes/HeapMemoryUsage` | Current heap memory usage |
| `jmx://java.lang:type=Runtime/attributes/Uptime` | JVM uptime in milliseconds |
| `jmx://java.lang:type=Threading/attributes/ThreadCount` | Current number of threads |
| `jmx://java.lang:type=GarbageCollector,name=G1_Young_Generation/attributes/CollectionCount` | GC collection count |

### Resource Content

Resources return the current value of the JMX attribute:

```json
{
  "contents": [
    {
      "uri": "jmx://java.lang:type=Memory/attributes/HeapMemoryUsage",
      "mimeType": "application/json",
      "text": "{\n  \"committed\": 536870912,\n  \"init\": 268435456,\n  \"max\": 8589934592,\n  \"used\": 245760000\n}"
    }
  ]
}
```

## Error Handling

### Common Error Responses

**Invalid ObjectName:**
```json
{
  "error": {
    "code": -32602,
    "message": "Invalid ObjectName format: invalid:name"
  }
}
```

**Attribute Not Found:**
```json
{
  "error": {
    "code": -32602, 
    "message": "Attribute 'InvalidAttribute' not found on MBean 'java.lang:type=Memory'"
  }
}
```

**Security Violation:**
```json
{
  "error": {
    "code": -32603,
    "message": "Operation not allowed: setAttribute on sensitive MBean"
  }
}
```

**Connection Error:**
```json
{
  "error": {
    "code": -32603,
    "message": "JMX connection failed: Connection refused"
  }
}
```

## Data Types

### Supported Java Types

The server automatically handles conversion between JSON and Java types:

| Java Type | JSON Type | Example |
|-----------|-----------|---------|
| `String` | `string` | `"Hello World"` |
| `int`, `Integer` | `number` | `42` |
| `long`, `Long` | `number` | `1234567890` |
| `boolean`, `Boolean` | `boolean` | `true` |
| `double`, `Double` | `number` | `3.14159` |
| `CompositeData` | `object` | `{"key": "value"}` |
| `TabularData` | `array` | `[{"row1": "data"}]` |

### Complex Types

**CompositeData** (e.g., MemoryUsage):
```json
{
  "committed": 536870912,
  "init": 268435456, 
  "max": 8589934592,
  "used": 245760000
}
```

**TabularData** (e.g., System Properties):
```json
[
  {"key": "java.version", "value": "17.0.1"},
  {"key": "os.name", "value": "Mac OS X"}
]
```

## Security Considerations

### Blocked Operations

The following operations are blocked by default for security:
- `shutdown`, `restart`, `stop`, `destroy`
- `gc` (garbage collection)
- `dumpHeap`
- `resetStatistics`

### Sensitive MBeans

Access to certain MBeans may be restricted:
- JVM diagnostic commands
- Security-related MBeans
- Platform-specific management beans

### Configuration

Security settings can be configured in `application.yml`:

```yaml
jmx:
  mcp:
    security:
      validate-parameters: true
      dangerous-operations:
        - "shutdown"
        - "restart"
      log-operations: true
```
