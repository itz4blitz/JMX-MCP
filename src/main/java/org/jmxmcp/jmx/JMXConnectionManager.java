package org.jmxmcp.jmx;

import org.jmxmcp.config.JmxConnectionProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.MBeanServerConnection;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Enhanced JMX connection manager that supports multiple connections through a registry.
 *
 * This class now handles:
 * - Multiple JMX connections via ConnectionRegistry
 * - Local platform MBean server access
 * - Remote JMX server connections with authentication
 * - Connection lifecycle management
 * - Connection health monitoring
 * - Backward compatibility with single-connection usage
 */
public class JMXConnectionManager {

    private static final Logger logger = LoggerFactory.getLogger(JMXConnectionManager.class);

    private final JmxConnectionProperties properties;
    private final JmxConnectionRegistry connectionRegistry;

    public JMXConnectionManager(JmxConnectionProperties properties, JmxConnectionRegistry connectionRegistry) {
        this.properties = properties;
        this.connectionRegistry = connectionRegistry;
        initializeDefaultConnection();
    }

    /**
     * Backward compatibility constructor for single connection
     */
    public JMXConnectionManager(JmxConnectionProperties properties) {
        this.properties = properties;
        this.connectionRegistry = new JmxConnectionRegistry();
        initializeDefaultConnection();
    }

    /**
     * Initializes the default JMX connection based on configuration.
     * For LOCAL type, we no longer auto-connect since we need to discover processes first.
     */
    private void initializeDefaultConnection() {
        try {
            if (properties.connection().type() == JmxConnectionProperties.ConnectionType.REMOTE) {
                String defaultId = "default";
                JmxConnectionInfo connectionInfo = createRemoteConnection(defaultId, "Default Remote Connection",
                    properties.connection().url(), properties.connection().username(),
                    properties.connection().password(), properties.connection().properties());

                connectionRegistry.addConnection(connectionInfo);
                connectToConnection(defaultId);
                logger.info("Default remote JMX connection initialized successfully");
            } else {
                logger.info("LOCAL connection type configured - use discovery to find and connect to local processes");
            }
        } catch (Exception e) {
            logger.error("Failed to initialize default JMX connection", e);
        }
    }



    /**
     * Creates a remote JMX connection
     */
    public JmxConnectionInfo createRemoteConnection(String id, String name, String url,
                                                   String username, String password,
                                                   Map<String, String> properties) {
        try {
            if (url == null || url.isBlank()) {
                throw new IllegalArgumentException("Remote JMX URL is required for remote connections");
            }

            logger.info("Creating remote JMX connection: {} -> {}", name, url);

            JMXServiceURL serviceURL = new JMXServiceURL(url);
            Map<String, Object> environment = createConnectionEnvironment(username, password, properties);

            JMXConnector connector = JMXConnectorFactory.connect(serviceURL, environment);
            MBeanServerConnection connection = connector.getMBeanServerConnection();
            int mbeanCount = connection.getMBeanCount();

            JmxConnectionInfo connectionInfo = JmxConnectionInfo.createRemote(id, name, url, username, password, properties);
            return connectionInfo.withConnection(connection, connector,
                JmxConnectionInfo.ConnectionStatus.CONNECTED, mbeanCount);
        } catch (IOException | RuntimeException e) {
            logger.error("Failed to create remote connection to {}: {}", url, e.getMessage());
            return JmxConnectionInfo.createRemote(id, name, url, username, password, properties)
                .withStatus(JmxConnectionInfo.ConnectionStatus.FAILED, e.getMessage());
        }
    }

    /**
     * Creates the connection environment for remote JMX connections.
     */
    private Map<String, Object> createConnectionEnvironment(String username, String password,
                                                           Map<String, String> properties) {
        Map<String, Object> environment = new HashMap<>();

        // Add custom properties if provided
        if (properties != null) {
            environment.putAll(properties);
        }

        // Add authentication if provided
        if (username != null && !username.isBlank()) {
            logger.info("Using authentication for JMX connection with username: {}", username);
            String[] credentials = {username, password != null ? password : ""};
            environment.put(JMXConnector.CREDENTIALS, credentials);
        }

        return environment;
    }

    /**
     * Connects to a specific connection in the registry
     */
    public boolean connectToConnection(String connectionId) {
        Optional<JmxConnectionInfo> connectionOpt = connectionRegistry.getConnection(connectionId);
        if (connectionOpt.isEmpty()) {
            logger.warn("Connection not found: {}", connectionId);
            return false;
        }

        JmxConnectionInfo connectionInfo = connectionOpt.get();

        try {
            if (connectionInfo.type() == JmxConnectionProperties.ConnectionType.LOCAL) {
                // LOCAL connections are deprecated - fail with informative message
                logger.warn("LOCAL connection type is deprecated. Use discovery service to find local processes.");
                JmxConnectionInfo failedConnection = connectionInfo.withStatus(
                    JmxConnectionInfo.ConnectionStatus.FAILED,
                    "LOCAL connections are deprecated. Use discovery service to find local processes and connect via JMX URLs.");
                connectionRegistry.updateConnection(failedConnection);
                return false;
            } else {
                // For remote connections (including local processes discovered via Attach API), create new connection
                JmxConnectionInfo updatedConnection = createRemoteConnection(
                    connectionInfo.id(), connectionInfo.name(), connectionInfo.url(),
                    connectionInfo.username(), connectionInfo.password(), connectionInfo.properties());
                connectionRegistry.updateConnection(updatedConnection);
            }

            connectionRegistry.setActiveConnection(connectionId);
            logger.info("Successfully connected to: {}", connectionId);
            return true;
        } catch (Exception e) {
            logger.error("Failed to connect to {}: {}", connectionId, e.getMessage());
            JmxConnectionInfo failedConnection = connectionInfo.withStatus(
                JmxConnectionInfo.ConnectionStatus.FAILED, e.getMessage());
            connectionRegistry.updateConnection(failedConnection);
            return false;
        }
    }

    /**
     * Gets the current active MBean server connection.
     *
     * @return the MBean server connection
     * @throws IllegalStateException if not connected
     */
    public MBeanServerConnection getConnection() {
        Optional<JmxConnectionInfo> activeConnection = connectionRegistry.getActiveConnection();
        if (activeConnection.isEmpty()) {
            throw new IllegalStateException("No active JMX connection available");
        }

        JmxConnectionInfo connectionInfo = activeConnection.get();
        if (!connectionInfo.isConnected()) {
            throw new IllegalStateException("Active JMX connection is not connected");
        }

        return connectionInfo.connection();
    }

    /**
     * Checks if there is an active JMX connection.
     */
    public boolean isConnected() {
        Optional<JmxConnectionInfo> activeConnection = connectionRegistry.getActiveConnection();
        if (activeConnection.isEmpty()) {
            return false;
        }

        JmxConnectionInfo connectionInfo = activeConnection.get();
        if (!connectionInfo.isConnected()) {
            return false;
        }

        try {
            // Test the connection by getting MBean count
            connectionInfo.connection().getMBeanCount();
            return true;
        } catch (IOException | RuntimeException e) {
            logger.warn("Active JMX connection test failed", e);
            // Update connection status to failed
            JmxConnectionInfo failedConnection = connectionInfo.withStatus(
                JmxConnectionInfo.ConnectionStatus.FAILED, e.getMessage());
            connectionRegistry.updateConnection(failedConnection);
            return false;
        }
    }

    /**
     * Attempts to reconnect to the active JMX server.
     */
    public void reconnect() {
        Optional<String> activeId = connectionRegistry.getActiveConnectionId();
        if (activeId.isPresent()) {
            logger.info("Attempting to reconnect to active JMX server: {}", activeId.get());
            connectToConnection(activeId.get());
        } else {
            logger.warn("No active connection to reconnect to");
        }
    }

    /**
     * Disconnects from the active JMX server.
     */
    public void disconnect() {
        Optional<JmxConnectionInfo> activeConnection = connectionRegistry.getActiveConnection();
        if (activeConnection.isPresent()) {
            JmxConnectionInfo connectionInfo = activeConnection.get();
            disconnectConnection(connectionInfo.id());
        }
    }

    /**
     * Disconnects a specific connection
     */
    public void disconnectConnection(String connectionId) {
        Optional<JmxConnectionInfo> connectionOpt = connectionRegistry.getConnection(connectionId);
        if (connectionOpt.isEmpty()) {
            logger.warn("Connection not found for disconnect: {}", connectionId);
            return;
        }

        JmxConnectionInfo connectionInfo = connectionOpt.get();

        // Close the connector if it exists
        if (connectionInfo.connector() != null) {
            try {
                connectionInfo.connector().close();
                logger.info("JMX connector closed for: {}", connectionId);
            } catch (IOException e) {
                logger.warn("Error closing JMX connector for {}: {}", connectionId, e.getMessage());
            }
        }

        // Update connection status
        JmxConnectionInfo disconnectedConnection = connectionInfo.withStatus(
            JmxConnectionInfo.ConnectionStatus.DISCONNECTED, null);
        connectionRegistry.updateConnection(disconnectedConnection);

        logger.info("JMX connection disconnected: {}", connectionId);
    }

    /**
     * Gets connection information for the active connection (backward compatibility).
     */
    public ConnectionInfo getConnectionInfo() {
        Optional<JmxConnectionInfo> activeConnection = connectionRegistry.getActiveConnection();
        if (activeConnection.isEmpty()) {
            return new ConnectionInfo(
                JmxConnectionProperties.ConnectionType.LOCAL,
                null,
                false,
                0
            );
        }

        JmxConnectionInfo connectionInfo = activeConnection.get();
        return new ConnectionInfo(
            connectionInfo.type(),
            connectionInfo.url(),
            connectionInfo.isConnected(),
            Objects.requireNonNullElse(connectionInfo.mbeanCount(), 0)
        );
    }

    /**
     * Gets the connection registry for advanced connection management
     */
    public JmxConnectionRegistry getConnectionRegistry() {
        return connectionRegistry;
    }

    /**
     * Adds a new connection to the registry
     */
    public boolean addConnection(String id, String name, JmxConnectionProperties.ConnectionType type,
                                String url, String username, String password, Map<String, String> properties) {
        try {
            JmxConnectionInfo connectionInfo;
            if (type == JmxConnectionProperties.ConnectionType.LOCAL) {
                connectionInfo = JmxConnectionInfo.createLocal(id, name);
            } else {
                connectionInfo = JmxConnectionInfo.createRemote(id, name, url, username, password, properties);
            }

            connectionRegistry.addConnection(connectionInfo);
            logger.info("Added new JMX connection: {}", id);
            return true;
        } catch (Exception e) {
            logger.error("Failed to add connection {}: {}", id, e.getMessage());
            return false;
        }
    }

    /**
     * Removes a connection from the registry
     */
    public boolean removeConnection(String connectionId) {
        return connectionRegistry.removeConnection(connectionId);
    }

    /**
     * Sets the active connection
     */
    public boolean setActiveConnection(String connectionId) {
        return connectionRegistry.setActiveConnection(connectionId);
    }

    /**
     * Connection information record for monitoring (backward compatibility).
     */
    public record ConnectionInfo(
        JmxConnectionProperties.ConnectionType type,
        String url,
        boolean connected,
        int mbeanCount
    ) {}
}
