package org.jmxmcp.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Configuration properties for JMX MCP Server.
 *
 * This class defines all configurable aspects of the JMX MCP server including
 * connection settings, discovery patterns, security options, and MCP server behavior.
 */
@Validated
public class JmxConnectionProperties {

    @NotNull
    private Connection connection;

    @NotNull
    private Discovery discovery;

    @NotNull
    private Tools tools;

    @NotNull
    private Resources resources;

    private Security security;

    /**
     * Multiple connection configurations (new multi-connection support)
     */
    private List<ConnectionConfig> connections;

    /**
     * JMX service discovery configuration
     */
    private ServiceDiscovery serviceDiscovery;

    // Default constructor
    public JmxConnectionProperties() {
        this.connection = new Connection(ConnectionType.LOCAL, null, null, null, null, null);
        this.discovery = new Discovery(List.of("*:*"), List.of(), Duration.ofSeconds(30), true);
        this.tools = new Tools(true, "jmx", 10, List.of());
        this.resources = new Resources(true, "jmx://", true, false, List.of());
        this.security = new Security(true, List.of("shutdown", "restart", "stop", "destroy"), true);
        this.connections = List.of();
        this.serviceDiscovery = new ServiceDiscovery(true, true, List.of(9999, 9010, 8999, 7199, 1099), List.of());
    }

    // Getters
    public Connection connection() { return connection; }
    public Discovery discovery() { return discovery; }
    public Tools tools() { return tools; }
    public Resources resources() { return resources; }
    public Security security() { return security; }
    public List<ConnectionConfig> connections() { return connections; }
    public ServiceDiscovery serviceDiscovery() { return serviceDiscovery; }

    // Setters for Spring Boot configuration binding
    public void setConnection(Connection connection) { this.connection = connection; }
    public void setDiscovery(Discovery discovery) { this.discovery = discovery; }
    public void setTools(Tools tools) { this.tools = tools; }
    public void setResources(Resources resources) { this.resources = resources; }
    public void setSecurity(Security security) { this.security = security; }
    public void setConnections(List<ConnectionConfig> connections) { this.connections = connections; }
    public void setServiceDiscovery(ServiceDiscovery serviceDiscovery) { this.serviceDiscovery = serviceDiscovery; }

    /**
     * JMX connection settings
     */
    public record Connection(
        /**
         * Connection type: LOCAL or REMOTE
         */
        @NotNull
        ConnectionType type,
        
        /**
         * JMX service URL for remote connections
         * Example: "service:jmx:rmi:///jndi/rmi://localhost:9999/jmxrmi"
         */
        String url,
        
        /**
         * Username for JMX authentication (optional)
         */
        String username,
        
        /**
         * Password for JMX authentication (optional)
         */
        String password,
        
        /**
         * Connection timeout
         */
        Duration timeout,
        
        /**
         * Additional connection properties
         */
        Map<String, String> properties
    ) {
        public Connection {
            if (timeout == null) {
                timeout = Duration.ofSeconds(30);
            }
            if (properties == null) {
                properties = Map.of();
            }
        }
    }

    /**
     * MBean discovery settings
     */
    public record Discovery(
        /**
         * ObjectName patterns to include (default: all)
         */
        @NotEmpty
        List<String> includePatterns,
        
        /**
         * ObjectName patterns to exclude
         */
        List<String> excludePatterns,
        
        /**
         * How often to refresh MBean discovery
         */
        Duration refreshInterval,
        
        /**
         * Whether to discover MBeans on startup
         */
        boolean discoverOnStartup
    ) {
        public Discovery {
            if (includePatterns == null || includePatterns.isEmpty()) {
                includePatterns = List.of("*:*");
            }
            if (excludePatterns == null) {
                excludePatterns = List.of();
            }
            if (refreshInterval == null) {
                refreshInterval = Duration.ofSeconds(30);
            }
        }
    }

    /**
     * MCP tools configuration
     */
    public record Tools(
        /**
         * Whether to enable JMX operations as MCP tools
         */
        boolean enabled,
        
        /**
         * Prefix for tool names
         */
        @NotBlank
        String prefix,
        
        /**
         * Maximum number of parameters for a tool
         */
        @Positive
        int maxParameters,
        
        /**
         * Operations to exclude from tool exposure
         */
        List<String> excludeOperations
    ) {
        public Tools {
            if (prefix == null || prefix.isBlank()) {
                prefix = "jmx";
            }
            if (maxParameters <= 0) {
                maxParameters = 10;
            }
            if (excludeOperations == null) {
                excludeOperations = List.of();
            }
        }
    }

    /**
     * MCP resources configuration
     */
    public record Resources(
        /**
         * Whether to enable JMX attributes as MCP resources
         */
        boolean enabled,
        
        /**
         * Base URI for resources
         */
        @NotBlank
        String baseUri,
        
        /**
         * Whether to include read-only attributes
         */
        boolean includeReadOnly,
        
        /**
         * Whether to include write-only attributes
         */
        boolean includeWriteOnly,
        
        /**
         * Attributes to exclude from resource exposure
         */
        List<String> excludeAttributes
    ) {
        public Resources {
            if (baseUri == null || baseUri.isBlank()) {
                baseUri = "jmx://";
            }
            if (excludeAttributes == null) {
                excludeAttributes = List.of();
            }
        }
    }

    /**
     * Security configuration
     */
    public record Security(
        /**
         * Whether to validate operation parameters
         */
        boolean validateParameters,
        
        /**
         * Operations that require confirmation
         */
        List<String> dangerousOperations,
        
        /**
         * Whether to log all JMX operations
         */
        boolean logOperations
    ) {
        public Security {
            if (dangerousOperations == null) {
                dangerousOperations = List.of("shutdown", "restart", "stop", "destroy");
            }
        }
    }

    /**
     * Individual connection configuration for multi-connection support
     */
    public record ConnectionConfig(
        /**
         * Unique identifier for this connection
         */
        @NotBlank
        String id,

        /**
         * Human-readable name for this connection
         */
        @NotBlank
        String name,

        /**
         * Connection type: LOCAL or REMOTE
         */
        @NotNull
        ConnectionType type,

        /**
         * JMX service URL for remote connections
         */
        String url,

        /**
         * Username for authentication (optional)
         */
        String username,

        /**
         * Password for authentication (optional)
         */
        String password,

        /**
         * Additional connection properties
         */
        Map<String, String> properties,

        /**
         * Whether this should be the default active connection
         */
        boolean defaultConnection
    ) {
        public ConnectionConfig {
            if (properties == null) {
                properties = Map.of();
            }
        }
    }

    /**
     * JMX service discovery configuration
     */
    public record ServiceDiscovery(
        /**
         * Whether to enable automatic service discovery
         */
        boolean enabled,

        /**
         * Whether to auto-register discovered connections
         */
        boolean autoRegister,

        /**
         * Ports to scan for JMX services
         */
        List<Integer> scanPorts,

        /**
         * Process names to exclude from discovery
         */
        List<String> excludeProcesses
    ) {
        public ServiceDiscovery {
            if (scanPorts == null) {
                scanPorts = List.of(9999, 9010, 8999, 7199, 1099);
            }
            if (excludeProcesses == null) {
                excludeProcesses = List.of();
            }
        }
    }

    /**
     * JMX connection types
     */
    public enum ConnectionType {
        LOCAL, REMOTE
    }


}
