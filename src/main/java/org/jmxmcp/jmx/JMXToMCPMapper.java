package org.jmxmcp.jmx;

import org.jmxmcp.config.JmxConnectionProperties;
import org.jmxmcp.model.MBeanInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.ObjectName;
import java.util.List;
import java.util.Map;

/**
 * Maps JMX concepts to MCP (Model Context Protocol) concepts.
 *
 * This mapper converts:
 * - JMX operations to MCP tools
 * - JMX attributes to MCP resources
 * - JMX types to JSON Schema types
 * - ObjectNames to URI-friendly identifiers
 *
 * Note: This class is configured as a bean in JmxMcpConfiguration.
 */
public class JMXToMCPMapper {

    private static final Logger logger = LoggerFactory.getLogger(JMXToMCPMapper.class);

    // Constants for JSON Schema types
    private static final String TYPE_OBJECT = "object";
    private static final String TYPE_STRING = "string";
    private static final String TYPE_ARRAY = "array";

    private final JmxConnectionProperties properties;

    public JMXToMCPMapper(JmxConnectionProperties properties) {
        this.properties = properties;
    }

    /**
     * Simple record for MCP Tool definition.
     */
    public record McpTool(String name, String description, Map<String, Object> inputSchema) {}

    /**
     * Simple record for MCP Resource definition.
     */
    public record McpResource(String uri, String name, String description, String mimeType) {}

    /**
     * Converts an MBean operation to an MCP tool definition.
     */
    public McpTool operationToTool(MBeanInfo mbeanInfo, MBeanInfo.MBeanOperationInfo operation) {
        String toolName = createToolName(mbeanInfo.objectName(), operation.name());
        String description = createOperationDescription(mbeanInfo, operation);

        // Create input schema for operation parameters
        Map<String, Object> inputSchema = createOperationInputSchema(operation);

        logger.debug("Mapping operation {} to tool {}", operation.name(), toolName);

        return new McpTool(toolName, description, inputSchema);
    }

    /**
     * Converts an MBean attribute to an MCP resource definition.
     */
    public McpResource attributeToResource(MBeanInfo mbeanInfo, MBeanInfo.MBeanAttributeInfo attribute) {
        String uri = createResourceUri(mbeanInfo.objectName(), attribute.name());
        String name = createResourceName(mbeanInfo.objectName(), attribute.name());
        String description = createAttributeDescription(mbeanInfo, attribute);
        String mimeType = "application/json";

        logger.debug("Mapping attribute {} to resource {}", attribute.name(), uri);

        return new McpResource(uri, name, description, mimeType);
    }

    /**
     * Creates a tool name from ObjectName and operation name.
     */
    public String createToolName(ObjectName objectName, String operationName) {
        String prefix = properties.tools().prefix();
        String sanitizedObjectName = sanitizeObjectName(objectName);
        return String.format("%s.%s.%s", prefix, sanitizedObjectName, operationName);
    }

    /**
     * Creates a resource URI from ObjectName and attribute name.
     */
    public String createResourceUri(ObjectName objectName, String attributeName) {
        String baseUri = properties.resources().baseUri();
        String sanitizedObjectName = sanitizeObjectName(objectName);
        return String.format("%s%s/attributes/%s", baseUri, sanitizedObjectName, attributeName);
    }

    /**
     * Creates a resource name from ObjectName and attribute name.
     */
    public String createResourceName(ObjectName objectName, String attributeName) {
        return String.format("%s.%s", sanitizeObjectName(objectName), attributeName);
    }

    /**
     * Sanitizes ObjectName for use in tool names and URIs.
     */
    private String sanitizeObjectName(ObjectName objectName) {
        return objectName.toString()
            .replace(":", "_")
            .replace("=", "_")
            .replace(",", "_")
            .replace(" ", "_")
            .replace("\"", "")
            .replaceAll("[^a-zA-Z0-9_.-]", "_");
    }

    /**
     * Creates a description for an operation tool.
     */
    private String createOperationDescription(MBeanInfo mbeanInfo, MBeanInfo.MBeanOperationInfo operation) {
        StringBuilder desc = new StringBuilder();
        desc.append(String.format("JMX Operation: %s.%s", mbeanInfo.objectName(), operation.name()));
        
        if (operation.description() != null && !operation.description().isBlank()) {
            desc.append("%n").append(operation.description());
        }

        desc.append(String.format("%nReturn Type: %s", operation.returnType()));
        desc.append(String.format("%nImpact: %s", operation.getImpactString()));

        if (!operation.parameters().isEmpty()) {
            desc.append("%nParameters:");
            for (MBeanInfo.MBeanParameterInfo param : operation.parameters()) {
                desc.append(String.format("%n  - %s (%s): %s",
                    param.name(), param.type(),
                    param.description() != null ? param.description() : "No description"));
            }
        }
        
        return desc.toString();
    }

    /**
     * Creates a description for an attribute resource.
     */
    private String createAttributeDescription(MBeanInfo mbeanInfo, MBeanInfo.MBeanAttributeInfo attribute) {
        StringBuilder desc = new StringBuilder();
        desc.append(String.format("JMX Attribute: %s.%s", mbeanInfo.objectName(), attribute.name()));
        
        if (attribute.description() != null && !attribute.description().isBlank()) {
            desc.append("%n").append(attribute.description());
        }

        desc.append(String.format("%nType: %s", attribute.type()));

        String access = "";
        if (attribute.readable() && attribute.writable()) {
            access = "Read/Write";
        } else if (attribute.readable()) {
            access = "Read-only";
        } else if (attribute.writable()) {
            access = "Write-only";
        }
        desc.append(String.format("%nAccess: %s", access));
        
        return desc.toString();
    }

    /**
     * Creates JSON Schema for operation input parameters.
     */
    private Map<String, Object> createOperationInputSchema(MBeanInfo.MBeanOperationInfo operation) {
        if (operation.parameters().isEmpty()) {
            return Map.of(
                "type", TYPE_OBJECT,
                "properties", Map.of(),
                "required", List.of()
            );
        }

        Map<String, Object> paramProperties = new java.util.HashMap<>();
        List<String> required = new java.util.ArrayList<>();

        for (MBeanInfo.MBeanParameterInfo param : operation.parameters()) {
            Map<String, Object> paramSchema = createParameterSchema(param);
            paramProperties.put(param.name(), paramSchema);
            required.add(param.name());
        }

        return Map.of(
            "type", TYPE_OBJECT,
            "properties", paramProperties,
            "required", required
        );
    }

    /**
     * Creates JSON Schema for a single parameter.
     */
    private Map<String, Object> createParameterSchema(MBeanInfo.MBeanParameterInfo param) {
        Map<String, Object> schema = new java.util.HashMap<>();
        
        // Map Java types to JSON Schema types
        String jsonType = mapJavaTypeToJsonType(param.type());
        schema.put("type", jsonType);
        
        if (param.description() != null && !param.description().isBlank()) {
            schema.put("description", param.description());
        }
        
        // Add format hints for specific types
        if (TYPE_STRING.equals(jsonType) && (param.type().contains("Date") || param.type().contains("Time"))) {
            schema.put("format", "date-time");
        }
        
        return schema;
    }

    /**
     * Maps Java types to JSON Schema types.
     */
    private String mapJavaTypeToJsonType(String javaType) {
        return switch (javaType) {
            case "boolean", "java.lang.Boolean" -> "boolean";
            case "byte", "short", "int", "long",
                 "java.lang.Byte", "java.lang.Short", "java.lang.Integer", "java.lang.Long" -> "integer";
            case "float", "double",
                 "java.lang.Float", "java.lang.Double", "java.math.BigDecimal" -> "number";
            case "char", "java.lang.Character", "java.lang.String" -> TYPE_STRING;
            default -> {
                if (javaType.startsWith("[")) {
                    yield TYPE_ARRAY;
                } else if (javaType.startsWith("java.util.List") ||
                          javaType.startsWith("java.util.Set") ||
                          javaType.startsWith("java.util.Collection")) {
                    yield TYPE_ARRAY;
                } else if (javaType.startsWith("java.util.Map")) {
                    yield TYPE_OBJECT;
                } else {
                    yield TYPE_STRING; // Default to string for complex types
                }
            }
        };
    }

    /**
     * Checks if an operation should be excluded from tool exposure.
     */
    public boolean shouldExcludeOperation(String operationName) {
        return properties.tools().excludeOperations().contains(operationName);
    }

    /**
     * Checks if an attribute should be excluded from resource exposure.
     */
    public boolean shouldExcludeAttribute(String attributeName) {
        return properties.resources().excludeAttributes().contains(attributeName);
    }

    /**
     * Checks if an attribute should be included based on access type.
     */
    public boolean shouldIncludeAttribute(MBeanInfo.MBeanAttributeInfo attribute) {
        return (attribute.readable() && properties.resources().includeReadOnly()) ||
               (attribute.writable() && properties.resources().includeWriteOnly());
    }
}
