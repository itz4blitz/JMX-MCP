package org.jmxmcp.jmx;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Registry for managing multiple JMX connections.
 * 
 * This class provides thread-safe management of multiple JMX connections,
 * including the concept of an "active" connection for backward compatibility
 * with existing single-connection code.
 */
@Component
public class JmxConnectionRegistry {
    
    private static final Logger logger = LoggerFactory.getLogger(JmxConnectionRegistry.class);
    
    private final Map<String, JmxConnectionInfo> connections = new ConcurrentHashMap<>();
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private volatile String activeConnectionId;
    
    /**
     * Adds a new connection to the registry
     */
    public void addConnection(JmxConnectionInfo connectionInfo) {
        lock.writeLock().lock();
        try {
            connections.put(connectionInfo.id(), connectionInfo);
            logger.info("Added JMX connection: {}", connectionInfo.getDisplayString());
            
            // If this is the first connection, make it active
            if (activeConnectionId == null) {
                activeConnectionId = connectionInfo.id();
                logger.info("Set {} as active connection (first connection)", connectionInfo.id());
            }
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Updates an existing connection in the registry
     */
    public void updateConnection(JmxConnectionInfo connectionInfo) {
        lock.writeLock().lock();
        try {
            if (connections.containsKey(connectionInfo.id())) {
                connections.put(connectionInfo.id(), connectionInfo);
                logger.debug("Updated JMX connection: {}", connectionInfo.id());
            } else {
                logger.warn("Attempted to update non-existent connection: {}", connectionInfo.id());
            }
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Removes a connection from the registry
     */
    public boolean removeConnection(String connectionId) {
        lock.writeLock().lock();
        try {
            JmxConnectionInfo removed = connections.remove(connectionId);
            if (removed != null) {
                // Close the connection if it's active
                closeConnection(removed);
                
                // If this was the active connection, choose a new one
                if (connectionId.equals(activeConnectionId)) {
                    activeConnectionId = connections.isEmpty() ? null : connections.keySet().iterator().next();
                    if (activeConnectionId != null) {
                        logger.info("Switched active connection to: {}", activeConnectionId);
                    }
                }
                
                logger.info("Removed JMX connection: {}", connectionId);
                return true;
            }
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Sets the active connection
     */
    public boolean setActiveConnection(String connectionId) {
        lock.writeLock().lock();
        try {
            if (connections.containsKey(connectionId)) {
                activeConnectionId = connectionId;
                logger.info("Set active JMX connection: {}", connectionId);
                return true;
            } else {
                logger.warn("Attempted to set non-existent connection as active: {}", connectionId);
                return false;
            }
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Gets the active connection
     */
    public Optional<JmxConnectionInfo> getActiveConnection() {
        lock.readLock().lock();
        try {
            if (activeConnectionId != null) {
                return Optional.ofNullable(connections.get(activeConnectionId));
            }
            return Optional.empty();
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Gets the active connection ID
     */
    public Optional<String> getActiveConnectionId() {
        lock.readLock().lock();
        try {
            return Optional.ofNullable(activeConnectionId);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Gets a specific connection by ID
     */
    public Optional<JmxConnectionInfo> getConnection(String connectionId) {
        lock.readLock().lock();
        try {
            return Optional.ofNullable(connections.get(connectionId));
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Gets all connections
     */
    public Collection<JmxConnectionInfo> getAllConnections() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(connections.values());
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Gets all connection IDs
     */
    public Set<String> getAllConnectionIds() {
        lock.readLock().lock();
        try {
            return new HashSet<>(connections.keySet());
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Checks if a connection exists
     */
    public boolean hasConnection(String connectionId) {
        lock.readLock().lock();
        try {
            return connections.containsKey(connectionId);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Gets the number of connections
     */
    public int getConnectionCount() {
        lock.readLock().lock();
        try {
            return connections.size();
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Closes all connections and clears the registry
     */
    public void closeAll() {
        lock.writeLock().lock();
        try {
            for (JmxConnectionInfo connection : connections.values()) {
                closeConnection(connection);
            }
            connections.clear();
            activeConnectionId = null;
            logger.info("Closed all JMX connections");
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Helper method to safely close a connection
     */
    private void closeConnection(JmxConnectionInfo connectionInfo) {
        if (connectionInfo.connector() != null) {
            try {
                connectionInfo.connector().close();
                logger.debug("Closed JMX connector for: {}", connectionInfo.id());
            } catch (IOException e) {
                logger.warn("Error closing JMX connector for {}: {}", connectionInfo.id(), e.getMessage());
            }
        }
    }
    
    /**
     * Gets summary information about all connections
     */
    public String getSummary() {
        lock.readLock().lock();
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("JMX Connection Registry Summary:\n");
            sb.append("Total connections: ").append(connections.size()).append("\n");
            sb.append("Active connection: ").append(activeConnectionId != null ? activeConnectionId : "None").append("\n");
            
            if (!connections.isEmpty()) {
                sb.append("\nConnections:\n");
                for (JmxConnectionInfo conn : connections.values()) {
                    sb.append("  ").append(conn.getDisplayString());
                    if (conn.id().equals(activeConnectionId)) {
                        sb.append(" [ACTIVE]");
                    }
                    sb.append("\n");
                }
            }
            
            return sb.toString();
        } finally {
            lock.readLock().unlock();
        }
    }
}
