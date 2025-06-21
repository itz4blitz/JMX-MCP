package org.jmxmcp.jmx;

import org.jmxmcp.config.JmxConnectionProperties;
import org.jmxmcp.model.MBeanInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.management.InstanceNotFoundException;
import javax.management.IntrospectionException;
import javax.management.MalformedObjectNameException;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Pattern;

/**
 * Service for discovering and managing MBeans from the JMX server.
 * 
 * This service:
 * - Discovers MBeans based on include/exclude patterns
 * - Caches MBean information for performance
 * - Provides periodic refresh of MBean registry
 * - Filters MBeans based on configuration
 */
@Service
public class MBeanDiscoveryService {

    private static final Logger logger = LoggerFactory.getLogger(MBeanDiscoveryService.class);

    private final JMXConnectionManager connectionManager;
    private final Map<ObjectName, MBeanInfo> mbeanCache = new ConcurrentHashMap<>();
    private final ReadWriteLock cacheLock = new ReentrantReadWriteLock();
    private final List<Pattern> includePatterns;
    private final List<Pattern> excludePatterns;

    public MBeanDiscoveryService(JMXConnectionManager connectionManager, JmxConnectionProperties properties) {
        this.connectionManager = connectionManager;
        this.includePatterns = compilePatterns(properties.discovery().includePatterns());
        this.excludePatterns = compilePatterns(properties.discovery().excludePatterns());

        if (properties.discovery().discoverOnStartup()) {
            discoverMBeans();
        }
    }

    /**
     * Compiles string patterns to regex patterns for ObjectName matching.
     */
    private List<Pattern> compilePatterns(List<String> patterns) {
        return patterns.stream()
            .map(pattern -> {
                // Convert JMX ObjectName pattern to regex
                String regex = pattern
                    .replace("*", ".*")
                    .replace("?", ".");
                return Pattern.compile(regex);
            })
            .toList();
    }

    /**
     * Discovers all MBeans from the JMX server and caches their information.
     */
    public final void discoverMBeans() {
        if (!connectionManager.isConnected()) {
            logger.warn("Cannot discover MBeans: JMX connection not available");
            return;
        }

        logger.info("Starting MBean discovery...");
        long startTime = System.currentTimeMillis();

        try {
            MBeanServerConnection connection = connectionManager.getConnection();
            Set<ObjectName> objectNames = connection.queryNames(null, null);
            
            cacheLock.writeLock().lock();
            try {
                mbeanCache.clear();
                
                for (ObjectName objectName : objectNames) {
                    if (shouldIncludeMBean(objectName)) {
                        loadMBeanInfo(connection, objectName);
                    }
                }
            } finally {
                cacheLock.writeLock().unlock();
            }
            
            long duration = System.currentTimeMillis() - startTime;
            logger.info("MBean discovery completed: {} MBeans discovered in {}ms", 
                       mbeanCache.size(), duration);
            
        } catch (IOException | RuntimeException e) {
            logger.error("Failed to discover MBeans", e);
        }
    }

    /**
     * Loads MBean information and adds it to the cache.
     */
    private void loadMBeanInfo(MBeanServerConnection connection, ObjectName objectName) {
        try {
            javax.management.MBeanInfo jmxInfo = connection.getMBeanInfo(objectName);
            MBeanInfo mbeanInfo = MBeanInfo.from(objectName, jmxInfo);
            mbeanCache.put(objectName, mbeanInfo);
        } catch (InstanceNotFoundException | IntrospectionException | ReflectionException | IOException | RuntimeException e) {
            logger.debug("Failed to get MBean info for {}: {}", objectName, e.getMessage());
        }
    }

    /**
     * Checks if an MBean should be included based on include/exclude patterns.
     */
    private boolean shouldIncludeMBean(ObjectName objectName) {
        String objectNameStr = objectName.toString();
        
        // Check exclude patterns first
        for (Pattern excludePattern : excludePatterns) {
            if (excludePattern.matcher(objectNameStr).matches()) {
                return false;
            }
        }
        
        // Check include patterns
        for (Pattern includePattern : includePatterns) {
            if (includePattern.matcher(objectNameStr).matches()) {
                return true;
            }
        }
        
        return false;
    }

    /**
     * Gets all discovered MBeans.
     */
    public Collection<MBeanInfo> getAllMBeans() {
        cacheLock.readLock().lock();
        try {
            return new ArrayList<>(mbeanCache.values());
        } finally {
            cacheLock.readLock().unlock();
        }
    }

    /**
     * Gets MBean information by ObjectName.
     */
    public Optional<MBeanInfo> getMBean(ObjectName objectName) {
        cacheLock.readLock().lock();
        try {
            return Optional.ofNullable(mbeanCache.get(objectName));
        } finally {
            cacheLock.readLock().unlock();
        }
    }

    /**
     * Gets MBean information by ObjectName string.
     */
    public Optional<MBeanInfo> getMBean(String objectNameStr) {
        try {
            ObjectName objectName = new ObjectName(objectNameStr);
            return getMBean(objectName);
        } catch (MalformedObjectNameException | RuntimeException e) {
            logger.debug("Invalid ObjectName: {}", objectNameStr);
            return Optional.empty();
        }
    }

    /**
     * Finds MBeans by domain.
     */
    public List<MBeanInfo> getMBeansByDomain(String domain) {
        cacheLock.readLock().lock();
        try {
            return mbeanCache.values().stream()
                .filter(mbean -> mbean.objectName().getDomain().equals(domain))
                .toList();
        } finally {
            cacheLock.readLock().unlock();
        }
    }

    /**
     * Finds MBeans by type property.
     */
    public List<MBeanInfo> getMBeansByType(String type) {
        cacheLock.readLock().lock();
        try {
            return mbeanCache.values().stream()
                .filter(mbean -> type.equals(mbean.objectName().getKeyProperty("type")))
                .toList();
        } finally {
            cacheLock.readLock().unlock();
        }
    }

    /**
     * Gets all unique domains from discovered MBeans.
     */
    public Set<String> getAllDomains() {
        cacheLock.readLock().lock();
        try {
            return mbeanCache.keySet().stream()
                .map(ObjectName::getDomain)
                .collect(java.util.stream.Collectors.toSet());
        } finally {
            cacheLock.readLock().unlock();
        }
    }

    /**
     * Gets discovery statistics.
     */
    public DiscoveryStats getDiscoveryStats() {
        cacheLock.readLock().lock();
        try {
            int totalMBeans = mbeanCache.size();
            int totalAttributes = mbeanCache.values().stream()
                .mapToInt(mbean -> mbean.attributes().size())
                .sum();
            int totalOperations = mbeanCache.values().stream()
                .mapToInt(mbean -> mbean.operations().size())
                .sum();
            
            return new DiscoveryStats(totalMBeans, totalAttributes, totalOperations, getAllDomains().size());
        } finally {
            cacheLock.readLock().unlock();
        }
    }

    /**
     * Scheduled method to refresh MBean discovery.
     */
    @Scheduled(fixedDelayString = "#{@jmxConnectionProperties.discovery().refreshInterval().toMillis()}")
    public void scheduledDiscovery() {
        if (connectionManager.isConnected()) {
            logger.debug("Performing scheduled MBean discovery");
            discoverMBeans();
        }
    }

    /**
     * Discovery statistics record.
     */
    public record DiscoveryStats(
        int totalMBeans,
        int totalAttributes,
        int totalOperations,
        int totalDomains
    ) {}
}
