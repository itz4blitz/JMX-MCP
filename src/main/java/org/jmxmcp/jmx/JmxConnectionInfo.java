package org.jmxmcp.jmx;

import org.jmxmcp.config.JmxConnectionProperties.ConnectionType;

import javax.management.MBeanServerConnection;
import javax.management.remote.JMXConnector;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Represents information about a JMX connection including connection details,
 * status, and the actual connection objects.
 * 
 * This record encapsulates all the information needed to manage a JMX connection
 * including metadata, connection parameters, and runtime state.
 */
public record JmxConnectionInfo(
    /**
     * Unique identifier for this connection
     */
    String id,
    
    /**
     * Human-readable name for this connection
     */
    String name,
    
    /**
     * Type of connection (LOCAL or REMOTE)
     */
    ConnectionType type,
    
    /**
     * JMX service URL for remote connections (null for local)
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
     * Current connection status
     */
    ConnectionStatus status,
    
    /**
     * When this connection was established
     */
    LocalDateTime connectedAt,
    
    /**
     * Last time this connection was tested/verified
     */
    LocalDateTime lastChecked,
    
    /**
     * Number of MBeans available through this connection
     */
    Integer mbeanCount,
    
    /**
     * Error message if connection failed
     */
    String errorMessage,
    
    /**
     * The actual MBean server connection (transient)
     */
    MBeanServerConnection connection,
    
    /**
     * The JMX connector for remote connections (transient)
     */
    JMXConnector connector
) {
    
    /**
     * Connection status enumeration
     */
    public enum ConnectionStatus {
        CONNECTED,
        DISCONNECTED,
        CONNECTING,
        FAILED,
        UNKNOWN
    }
    
    /**
     * Creates a new connection info for a local connection
     */
    public static JmxConnectionInfo createLocal(String id, String name) {
        return new JmxConnectionInfo(
            id,
            name,
            ConnectionType.LOCAL,
            null, // no URL for local
            null, // no username for local
            null, // no password for local
            Map.of(),
            ConnectionStatus.DISCONNECTED,
            null,
            null,
            null,
            null,
            null,
            null
        );
    }
    
    /**
     * Creates a new connection info for a remote connection
     */
    public static JmxConnectionInfo createRemote(String id, String name, String url, 
                                                String username, String password, 
                                                Map<String, String> properties) {
        return new JmxConnectionInfo(
            id,
            name,
            ConnectionType.REMOTE,
            url,
            username,
            password,
            properties != null ? properties : Map.of(),
            ConnectionStatus.DISCONNECTED,
            null,
            null,
            null,
            null,
            null,
            null
        );
    }
    
    /**
     * Creates a copy with updated connection objects and status
     */
    public JmxConnectionInfo withConnection(MBeanServerConnection connection, 
                                          JMXConnector connector, 
                                          ConnectionStatus status,
                                          Integer mbeanCount) {
        return new JmxConnectionInfo(
            id, name, type, url, username, password, properties,
            status,
            status == ConnectionStatus.CONNECTED ? LocalDateTime.now() : connectedAt,
            LocalDateTime.now(),
            mbeanCount,
            status == ConnectionStatus.CONNECTED ? null : errorMessage,
            connection,
            connector
        );
    }
    
    /**
     * Creates a copy with updated status and error message
     */
    public JmxConnectionInfo withStatus(ConnectionStatus status, String errorMessage) {
        return new JmxConnectionInfo(
            id, name, type, url, username, password, properties,
            status,
            connectedAt,
            LocalDateTime.now(),
            status == ConnectionStatus.CONNECTED ? mbeanCount : null,
            errorMessage,
            status == ConnectionStatus.CONNECTED ? connection : null,
            status == ConnectionStatus.CONNECTED ? connector : null
        );
    }
    
    /**
     * Checks if this connection is currently active/connected
     */
    public boolean isConnected() {
        return status == ConnectionStatus.CONNECTED && connection != null;
    }
    
    /**
     * Gets a display string for this connection
     */
    public String getDisplayString() {
        StringBuilder sb = new StringBuilder();
        sb.append(name).append(" (").append(id).append(")");
        sb.append(" - ").append(type);
        if (url != null) {
            sb.append(" @ ").append(url);
        }
        sb.append(" [").append(status).append("]");
        if (mbeanCount != null) {
            sb.append(" - ").append(mbeanCount).append(" MBeans");
        }
        return sb.toString();
    }
}
