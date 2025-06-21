package org.jmxmcp.service;

import org.jmxmcp.config.JmxConnectionProperties;
import org.jmxmcp.config.JmxSecurityValidator;
import org.jmxmcp.jmx.JMXConnectionManager;
import org.jmxmcp.jmx.JmxConnectionInfo;
import org.jmxmcp.jmx.JmxConnectionRegistry;
import org.jmxmcp.jmx.MBeanDiscoveryService;
import org.jmxmcp.model.MBeanInfo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

import javax.management.Attribute;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service providing JMX operations for MCP tools.
 */
@Service
public class JmxService {

    private static final Logger logger = LoggerFactory.getLogger(JmxService.class);
    private static final String SECURITY_VALIDATION_FAILED = "Security validation failed: ";
    private static final String ERROR_GETTING_ATTRIBUTE = "Error getting attribute: ";
    private static final String ERROR_SETTING_ATTRIBUTE = "Error setting attribute: ";

    private final MBeanDiscoveryService discoveryService;
    private final JMXConnectionManager connectionManager;
    private final JmxSecurityValidator securityValidator;
    private final JmxDiscoveryService jmxDiscoveryService;

    public JmxService(
            MBeanDiscoveryService discoveryService,
            JMXConnectionManager connectionManager,
            JmxSecurityValidator securityValidator,
            JmxDiscoveryService jmxDiscoveryService) {
        this.discoveryService = discoveryService;
        this.connectionManager = connectionManager;
        this.securityValidator = securityValidator;
        this.jmxDiscoveryService = jmxDiscoveryService;
    }

    @Tool(description = "List all discovered MBeans with their basic information. Optionally filter by domain.")
    public String listMBeans(String domain) {
        try {
            Collection<MBeanInfo> mbeans;
            if (domain != null && !domain.trim().isEmpty()) {
                mbeans = discoveryService.getMBeansByDomain(domain.trim());
            } else {
                mbeans = discoveryService.getAllMBeans();
            }
            
            if (mbeans.isEmpty()) {
                return domain != null ? 
                    "No MBeans found in domain: " + domain : 
                    "No MBeans discovered";
            }
            
            StringBuilder result = new StringBuilder();
            result.append("Found ").append(mbeans.size()).append(" MBeans");
            if (domain != null) {
                result.append(" in domain '").append(domain).append("'");
            }
            result.append(":\n\n");
            
            for (MBeanInfo mbean : mbeans) {
                result.append("• ").append(mbean.objectName()).append("\n");
                result.append("  Class: ").append(mbean.className()).append("\n");
                if (mbean.description() != null && !mbean.description().isEmpty()) {
                    result.append("  Description: ").append(mbean.description()).append("\n");
                }
                result.append("  Attributes: ").append(mbean.attributes().size());
                result.append(", Operations: ").append(mbean.operations().size()).append("\n\n");
            }
            
            return result.toString();
            
        } catch (Exception e) {
            logger.error("Error listing MBeans", e);
            return "Error listing MBeans: " + e.getMessage();
        }
    }

    @Tool(description = "Get detailed information about a specific MBean including all attributes and operations")
    public String getMBeanInfo(String objectName) {
        try {
            Optional<MBeanInfo> mbeanOpt = discoveryService.getMBean(objectName);
            if (mbeanOpt.isEmpty()) {
                return "MBean not found: " + objectName;
            }
            
            MBeanInfo mbean = mbeanOpt.get();
            StringBuilder result = new StringBuilder();
            
            result.append("MBean Information:\n");
            result.append("==================\n");
            result.append("ObjectName: ").append(mbean.objectName()).append("\n");
            result.append("Class: ").append(mbean.className()).append("\n");
            result.append("Description: ").append(mbean.description() != null ? mbean.description() : "N/A").append("\n\n");
            
            // Attributes
            result.append("Attributes (").append(mbean.attributes().size()).append("):\n");
            result.append("-------------------\n");
            for (MBeanInfo.MBeanAttributeInfo attr : mbean.attributes()) {
                result.append("• ").append(attr.name()).append(" (").append(attr.type()).append(")");
                
                List<String> access = new ArrayList<>();
                if (attr.readable()) access.add("R");
                if (attr.writable()) access.add("W");
                result.append(" [").append(String.join("/", access)).append("]");
                
                if (attr.description() != null && !attr.description().isEmpty()) {
                    result.append("\n  ").append(attr.description());
                }
                result.append("\n");
            }
            
            // Operations
            result.append("\nOperations (").append(mbean.operations().size()).append("):\n");
            result.append("-------------------\n");
            for (MBeanInfo.MBeanOperationInfo op : mbean.operations()) {
                result.append("• ").append(op.name()).append("(");
                result.append(op.parameters().stream()
                    .map(p -> p.type() + " " + p.name())
                    .collect(Collectors.joining(", ")));
                result.append(") : ").append(op.returnType()).append("\n");
                
                if (op.description() != null && !op.description().isEmpty()) {
                    result.append("  ").append(op.description()).append("\n");
                }
                result.append("  Impact: ").append(op.getImpactString()).append("\n");
            }
            
            return result.toString();
            
        } catch (Exception e) {
            logger.error("Error getting MBean info", e);
            return "Error getting MBean info: " + e.getMessage();
        }
    }

    @Tool(description = "Get the value of an MBean attribute")
    public String getAttribute(String objectName, String attributeName) {
        try {
            ObjectName objName = new ObjectName(objectName);
            
            // Security validation
            var validation = securityValidator.validateObjectName(objName);
            if (!validation.isValid()) {
                return SECURITY_VALIDATION_FAILED + validation.getErrorMessage();
            }
            
            MBeanServerConnection connection = connectionManager.getConnection();
            Object value = connection.getAttribute(objName, attributeName);
            
            return "Attribute '" + attributeName + "' = " + 
                   (value != null ? value.toString() : "null") +
                   " (type: " + (value != null ? value.getClass().getSimpleName() : "null") + ")";
                   
        } catch (javax.management.MalformedObjectNameException e) {
            logger.error("Invalid ObjectName: {}", objectName, e);
            return ERROR_GETTING_ATTRIBUTE + "Invalid ObjectName format - " + e.getMessage();
        } catch (javax.management.MBeanException | javax.management.AttributeNotFoundException |
                 javax.management.InstanceNotFoundException | javax.management.ReflectionException e) {
            logger.error("JMX error getting attribute", e);
            return ERROR_GETTING_ATTRIBUTE + e.getMessage();
        } catch (java.io.IOException e) {
            logger.error("Connection error getting attribute", e);
            return ERROR_GETTING_ATTRIBUTE + "Connection failed - " + e.getMessage();
        } catch (IllegalStateException e) {
            logger.error("Connection state error getting attribute", e);
            return ERROR_GETTING_ATTRIBUTE + e.getMessage();
        } catch (RuntimeException e) {
            logger.error("Unexpected error getting attribute", e);
            return ERROR_GETTING_ATTRIBUTE + e.getMessage();
        }
    }

    @Tool(description = "Set the value of an MBean attribute")
    public String setAttribute(String objectName, String attributeName, String value) {
        try {
            ObjectName objName = new ObjectName(objectName);
            
            // Security validation
            var validation = securityValidator.validateObjectName(objName);
            if (!validation.isValid()) {
                return SECURITY_VALIDATION_FAILED + validation.getErrorMessage();
            }
            
            MBeanServerConnection connection = connectionManager.getConnection();
            
            // Get attribute info to determine type
            Optional<MBeanInfo> mbeanOpt = discoveryService.getMBean(objectName);
            if (mbeanOpt.isEmpty()) {
                return "MBean not found: " + objectName;
            }
            
            MBeanInfo.MBeanAttributeInfo attrInfo = mbeanOpt.get().getAttribute(attributeName);
            if (attrInfo == null) {
                return "Attribute not found: " + attributeName;
            }
            
            if (!attrInfo.writable()) {
                return "Attribute '" + attributeName + "' is not writable";
            }
            
            // Convert string value to appropriate type
            Object convertedValue = convertStringToType(value, attrInfo.type());
            
            Attribute attribute = new Attribute(attributeName, convertedValue);
            connection.setAttribute(objName, attribute);
            
            return "Successfully set attribute '" + attributeName + "' to '" + value + "'";
            
        } catch (javax.management.MalformedObjectNameException e) {
            logger.error("Invalid ObjectName: {}", objectName, e);
            return ERROR_SETTING_ATTRIBUTE + "Invalid ObjectName format - " + e.getMessage();
        } catch (javax.management.MBeanException | javax.management.AttributeNotFoundException |
                 javax.management.InstanceNotFoundException | javax.management.ReflectionException |
                 javax.management.InvalidAttributeValueException e) {
            logger.error("JMX error setting attribute", e);
            return ERROR_SETTING_ATTRIBUTE + e.getMessage();
        } catch (java.io.IOException e) {
            logger.error("Connection error setting attribute", e);
            return ERROR_SETTING_ATTRIBUTE + "Connection failed - " + e.getMessage();
        } catch (IllegalStateException e) {
            logger.error("Connection state error setting attribute", e);
            return ERROR_SETTING_ATTRIBUTE + e.getMessage();
        } catch (NumberFormatException e) {
            logger.error("Invalid value format for attribute", e);
            return ERROR_SETTING_ATTRIBUTE + "Invalid value format - " + e.getMessage();
        } catch (RuntimeException e) {
            logger.error("Unexpected error setting attribute", e);
            return ERROR_SETTING_ATTRIBUTE + e.getMessage();
        }
    }

    @Tool(description = "List all MBean domains")
    public String listDomains() {
        try {
            Set<String> domains = discoveryService.getAllDomains();
            
            if (domains.isEmpty()) {
                return "No domains found";
            }
            
            StringBuilder result = new StringBuilder();
            result.append("MBean Domains (").append(domains.size()).append("):\n");
            
            domains.stream().sorted().forEach(domain -> 
                result.append("• ").append(domain).append("\n"));
            
            return result.toString();
            
        } catch (Exception e) {
            logger.error("Error listing domains", e);
            return "Error listing domains: " + e.getMessage();
        }
    }

    @Tool(description = "Get JMX connection status and information")
    public String getConnectionInfo() {
        try {
            JMXConnectionManager.ConnectionInfo info = connectionManager.getConnectionInfo();
            MBeanDiscoveryService.DiscoveryStats stats = discoveryService.getDiscoveryStats();
            
            StringBuilder result = new StringBuilder();
            result.append("JMX Connection Information:\n");
            result.append("===========================\n");
            result.append("Connection Type: ").append(info.type()).append("\n");
            result.append("URL: ").append(info.url() != null ? info.url() : "N/A (local)").append("\n");
            result.append("Connected: ").append(info.connected() ? "Yes" : "No").append("\n");
            result.append("Total MBeans: ").append(stats.totalMBeans()).append("\n");
            result.append("Total Attributes: ").append(stats.totalAttributes()).append("\n");
            result.append("Total Operations: ").append(stats.totalOperations()).append("\n");
            result.append("Total Domains: ").append(stats.totalDomains()).append("\n");
            
            return result.toString();
            
        } catch (Exception e) {
            logger.error("Error getting connection info", e);
            return "Error getting connection info: " + e.getMessage();
        }
    }

    // ========================================
    // Connection Management Tools
    // ========================================

    @Tool(description = "List all available JMX connections including their status and details")
    public String listJmxConnections() {
        try {
            JmxConnectionRegistry registry = connectionManager.getConnectionRegistry();
            Collection<JmxConnectionInfo> connections = registry.getAllConnections();
            Optional<String> activeId = registry.getActiveConnectionId();

            if (connections.isEmpty()) {
                return "No JMX connections configured. Use discoverJmxServices() to find available services.";
            }

            StringBuilder result = new StringBuilder();
            result.append("JMX Connections (").append(connections.size()).append("):\n");
            result.append("=====================================\n");

            for (JmxConnectionInfo conn : connections) {
                result.append("• ").append(conn.name()).append(" (").append(conn.id()).append(")\n");
                result.append("  Type: ").append(conn.type()).append("\n");
                if (conn.url() != null) {
                    result.append("  URL: ").append(conn.url()).append("\n");
                }
                result.append("  Status: ").append(conn.status());
                if (conn.id().equals(activeId.orElse(null))) {
                    result.append(" [ACTIVE]");
                }
                result.append("\n");
                if (conn.mbeanCount() != null) {
                    result.append("  MBeans: ").append(conn.mbeanCount()).append("\n");
                }
                if (conn.errorMessage() != null) {
                    result.append("  Error: ").append(conn.errorMessage()).append("\n");
                }
                result.append("\n");
            }

            return result.toString();

        } catch (Exception e) {
            logger.error("Error listing JMX connections", e);
            return "Error listing JMX connections: " + e.getMessage();
        }
    }

    @Tool(description = "Switch to a different JMX connection by connection ID")
    public String switchJmxConnection(String connectionId) {
        try {
            JmxConnectionRegistry registry = connectionManager.getConnectionRegistry();

            if (!registry.hasConnection(connectionId)) {
                return "Connection not found: " + connectionId + ". Use listJmxConnections() to see available connections.";
            }

            boolean success = connectionManager.connectToConnection(connectionId);
            if (success) {
                Optional<JmxConnectionInfo> connection = registry.getConnection(connectionId);
                String name = connection.map(JmxConnectionInfo::name).orElse(connectionId);
                return "Successfully switched to JMX connection: " + name + " (" + connectionId + ")";
            } else {
                return "Failed to switch to connection: " + connectionId + ". Check connection details and try again.";
            }

        } catch (Exception e) {
            logger.error("Error switching JMX connection", e);
            return "Error switching JMX connection: " + e.getMessage();
        }
    }

    @Tool(description = "Discover JMX-enabled applications on the local system")
    public String discoverJmxServices() {
        try {
            JmxDiscoveryService.DiscoveryResult result = jmxDiscoveryService.discoverJmxServices();

            StringBuilder response = new StringBuilder();
            response.append("JMX Service Discovery Results:\n");
            response.append("==============================\n");
            response.append("Discovered ").append(result.getDiscoveredCount()).append(" JMX services\n\n");

            if (result.getDiscoveredCount() > 0) {
                for (JmxConnectionInfo conn : result.discoveredConnections()) {
                    response.append("• ").append(conn.name()).append("\n");
                    response.append("  ID: ").append(conn.id()).append("\n");
                    response.append("  URL: ").append(conn.url()).append("\n");
                    if (conn.mbeanCount() != null) {
                        response.append("  MBeans: ").append(conn.mbeanCount()).append("\n");
                    }
                    response.append("\n");
                }

                response.append("Use addJmxConnection() to add any of these to your connection registry,\n");
                response.append("or use autoRegisterDiscoveredConnections() to add them all automatically.");
            } else {
                response.append("No JMX services found. Make sure your applications are running with JMX enabled.\n");
                response.append("Common JMX configuration:\n");
                response.append("  -Dcom.sun.management.jmxremote\n");
                response.append("  -Dcom.sun.management.jmxremote.port=9999\n");
                response.append("  -Dcom.sun.management.jmxremote.authenticate=false\n");
                response.append("  -Dcom.sun.management.jmxremote.ssl=false");
            }

            if (result.hasErrors()) {
                response.append("\n\nErrors encountered:\n");
                for (String error : result.errors()) {
                    response.append("• ").append(error).append("\n");
                }
            }

            return response.toString();

        } catch (Exception e) {
            logger.error("Error discovering JMX services", e);
            return "Error discovering JMX services: " + e.getMessage();
        }
    }

    @Tool(description = "Automatically register all discovered JMX connections")
    public String autoRegisterDiscoveredConnections() {
        try {
            int registered = jmxDiscoveryService.autoRegisterDiscoveredConnections();

            if (registered > 0) {
                return "Successfully auto-registered " + registered + " JMX connections. " +
                       "Use listJmxConnections() to see all available connections.";
            } else {
                return "No new JMX connections were registered. All discovered connections may already be registered, " +
                       "or no services were found. Use discoverJmxServices() to see what's available.";
            }

        } catch (Exception e) {
            logger.error("Error auto-registering JMX connections", e);
            return "Error auto-registering JMX connections: " + e.getMessage();
        }
    }

    @Tool(description = "Add a new JMX connection manually")
    public String addJmxConnection(String id, String name, String type, String url, String username, String password) {
        try {
            JmxConnectionProperties.ConnectionType connectionType = parseConnectionType(type);
            if (connectionType == null) {
                return "Invalid connection type: " + type + ". Must be LOCAL or REMOTE.";
            }

            Map<String, String> properties = new HashMap<>();
            boolean success = connectionManager.addConnection(id, name, connectionType, url, username, password, properties);

            if (success) {
                return "Successfully added JMX connection: " + name + " (" + id + ")";
            } else {
                return "Failed to add JMX connection: " + id + ". Check the connection details.";
            }

        } catch (Exception e) {
            logger.error("Error adding JMX connection", e);
            return "Error adding JMX connection: " + e.getMessage();
        }
    }

    @Tool(description = "Remove a JMX connection by ID")
    public String removeJmxConnection(String connectionId) {
        try {
            boolean success = connectionManager.removeConnection(connectionId);

            if (success) {
                return "Successfully removed JMX connection: " + connectionId;
            } else {
                return "Connection not found: " + connectionId;
            }

        } catch (Exception e) {
            logger.error("Error removing JMX connection", e);
            return "Error removing JMX connection: " + e.getMessage();
        }
    }

    /**
     * Parses a connection type string to a ConnectionType enum.
     *
     * @param type the connection type string
     * @return the ConnectionType enum or null if invalid
     */
    private JmxConnectionProperties.ConnectionType parseConnectionType(String type) {
        try {
            return JmxConnectionProperties.ConnectionType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private Object convertStringToType(String value, String typeName) {
        if (value == null) return null;

        return switch (typeName) {
            case "boolean", "java.lang.Boolean" -> Boolean.parseBoolean(value);
            case "byte", "java.lang.Byte" -> Byte.parseByte(value);
            case "short", "java.lang.Short" -> Short.parseShort(value);
            case "int", "java.lang.Integer" -> Integer.parseInt(value);
            case "long", "java.lang.Long" -> Long.parseLong(value);
            case "float", "java.lang.Float" -> Float.parseFloat(value);
            case "double", "java.lang.Double" -> Double.parseDouble(value);
            case "char", "java.lang.Character" -> !value.isEmpty() ? value.charAt(0) : '\0';
            default -> value; // String or complex type
        };
    }
}
