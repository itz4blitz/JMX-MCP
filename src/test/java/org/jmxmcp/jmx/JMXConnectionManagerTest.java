package org.jmxmcp.jmx;

import org.jmxmcp.config.JmxConnectionProperties;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for JMXConnectionManager.
 */
class JMXConnectionManagerTest {

    @Test
    void testLocalConnectionInitialization() {
        // Create test properties for local connection using default constructor
        JmxConnectionProperties properties = new JmxConnectionProperties();
        JMXConnectionManager connectionManager = new JMXConnectionManager(properties);

        // With the new architecture, LOCAL connections don't auto-connect
        // They require discovery service to find and connect to processes
        assertFalse(connectionManager.isConnected());

        // Verify that getConnectionInfo returns appropriate defaults for no active connection
        JMXConnectionManager.ConnectionInfo info = connectionManager.getConnectionInfo();
        assertNotNull(info);
        assertEquals(JmxConnectionProperties.ConnectionType.LOCAL, info.type());
        assertFalse(info.connected());
        assertEquals(0, info.mbeanCount());
    }

    @Test
    void testConnectionInfo() {
        // Create test properties for local connection
        JmxConnectionProperties properties = new JmxConnectionProperties();
        JMXConnectionManager connectionManager = new JMXConnectionManager(properties);

        // Test connection info when no active connection exists
        JMXConnectionManager.ConnectionInfo info = connectionManager.getConnectionInfo();
        assertNotNull(info);
        assertEquals(JmxConnectionProperties.ConnectionType.LOCAL, info.type());
        assertFalse(info.connected());
        assertEquals(0, info.mbeanCount());
    }

    @Test
    void testGetConnectionWithNoActiveConnection() {
        // Create test properties for local connection
        JmxConnectionProperties properties = new JmxConnectionProperties();
        JMXConnectionManager connectionManager = new JMXConnectionManager(properties);

        // Should throw exception when no active connection is available
        IllegalStateException exception = assertThrows(IllegalStateException.class,
            connectionManager::getConnection);
        assertEquals("No active JMX connection available", exception.getMessage());
    }

    @Test
    void testAddConnection() {
        // Create test properties for local connection
        JmxConnectionProperties properties = new JmxConnectionProperties();
        JMXConnectionManager connectionManager = new JMXConnectionManager(properties);

        // Test adding a new connection to the registry
        boolean result = connectionManager.addConnection(
            "test-connection",
            "Test Connection",
            JmxConnectionProperties.ConnectionType.LOCAL,
            null,
            null,
            null,
            Map.of()
        );

        assertTrue(result);

        // Verify the connection was added to the registry
        assertTrue(connectionManager.getConnectionRegistry().hasConnection("test-connection"));
    }

    @Test
    void testConnectionRegistry() {
        // Create test properties for local connection
        JmxConnectionProperties properties = new JmxConnectionProperties();
        JMXConnectionManager connectionManager = new JMXConnectionManager(properties);

        // Test that we can get the connection registry
        assertNotNull(connectionManager.getConnectionRegistry());

        // Initially should have no connections
        assertFalse(connectionManager.getConnectionRegistry().getActiveConnection().isPresent());
    }
}
