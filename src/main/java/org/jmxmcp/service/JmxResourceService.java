package org.jmxmcp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jmxmcp.config.JmxConnectionProperties;
import org.jmxmcp.config.JmxSecurityValidator;
import org.jmxmcp.jmx.JMXConnectionManager;
import org.jmxmcp.jmx.JMXToMCPMapper;
import org.jmxmcp.jmx.MBeanDiscoveryService;
import org.jmxmcp.model.MBeanInfo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.server.McpServerFeatures;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;



/**
 * Service that exposes JMX attributes as MCP resources.
 * 
 * This service:
 * - Discovers JMX attributes and registers them as MCP resources
 * - Handles resource URI parsing and validation
 * - Provides attribute values as JSON resources
 * - Integrates with existing JMX infrastructure for security and caching
 */
@Service
public class JmxResourceService {

    private static final Logger logger = LoggerFactory.getLogger(JmxResourceService.class);

    // Constants
    private static final String APPLICATION_JSON = "application/json";
    private static final String JMX_URI_PREFIX = "jmx://";
    private static final String TIMESTAMP_KEY = "timestamp";

    // URI pattern: jmx://domain_type_property/attributes/attributeName
    private static final Pattern RESOURCE_URI_PATTERN = Pattern.compile(
        "^" + Pattern.quote(JMX_URI_PREFIX) + "([^/]+)/attributes/([^/]+)$"
    );
    
    private final MBeanDiscoveryService discoveryService;
    private final JMXConnectionManager connectionManager;
    private final JmxSecurityValidator securityValidator;
    private final JMXToMCPMapper mapper;
    private final JmxConnectionProperties properties;
    private final ObjectMapper objectMapper;
    
    // Cache for resource specifications
    private final Map<String, McpSchema.Resource> resourceCache = new ConcurrentHashMap<>();
    private final ReadWriteLock resourceCacheLock = new ReentrantReadWriteLock();
    private volatile boolean resourcesInitialized = false;

    // Cache for attribute values with TTL
    private final Map<String, CachedAttributeValue> attributeValueCache = new ConcurrentHashMap<>();
    private final ReadWriteLock attributeCacheLock = new ReentrantReadWriteLock();

    // Resource change notification system
    private final List<ResourceChangeListener> changeListeners = new CopyOnWriteArrayList<>();
    private final Map<String, Object> lastKnownValues = new ConcurrentHashMap<>();

    public JmxResourceService(
            MBeanDiscoveryService discoveryService,
            JMXConnectionManager connectionManager,
            JmxSecurityValidator securityValidator,
            JMXToMCPMapper mapper,
            JmxConnectionProperties properties) {
        this.discoveryService = discoveryService;
        this.connectionManager = connectionManager;
        this.securityValidator = securityValidator;
        this.mapper = mapper;
        this.properties = properties;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Registers JMX attributes as MCP resources.
     * This method is called by Spring AI MCP framework to discover available resources.
     */
    @Bean
    public List<McpServerFeatures.SyncResourceSpecification> jmxResources() {
        if (!properties.resources().enabled()) {
            logger.info("JMX resources are disabled");
            return List.of();
        }

        logger.info("Registering JMX attributes as MCP resources");

        // Ensure resources are discovered
        ensureResourcesInitialized();

        // Create resource specifications for each discovered JMX attribute
        List<McpServerFeatures.SyncResourceSpecification> specifications = new ArrayList<>();

        resourceCacheLock.readLock().lock();
        try {
            for (McpSchema.Resource resource : resourceCache.values()) {
                var resourceSpec = new McpServerFeatures.SyncResourceSpecification(
                    resource,
                    this::readResource
                );
                specifications.add(resourceSpec);
            }
        } finally {
            resourceCacheLock.readLock().unlock();
        }

        logger.info("Registered {} JMX attribute resources", specifications.size());
        return specifications;
    }



    /**
     * Reads the value of a specific JMX attribute resource.
     */
    @SuppressWarnings({"unused", "java:S1172"}) // exchange parameter required by Spring AI MCP framework
    private McpSchema.ReadResourceResult readResource(@SuppressWarnings("unused") Object exchange, McpSchema.ReadResourceRequest request) {
        String uri = request.uri();
        logger.debug("Reading JMX resource: {}", uri);

        // Note: exchange parameter is required by Spring AI MCP framework but not used in this implementation

        try {
            ResourceUriInfo uriInfo = parseResourceUri(uri);
            if (uriInfo == null) {
                throw new IllegalArgumentException("Invalid resource URI format: " + uri);
            }

            // Security validation for ObjectName
            var objectNameValidation = securityValidator.validateObjectName(uriInfo.objectName());
            if (!objectNameValidation.isValid()) {
                logger.warn("Security validation failed for ObjectName {}: {}",
                    uriInfo.objectName(), objectNameValidation.getErrorMessage());
                throw new SecurityException("Security validation failed: " + objectNameValidation.getErrorMessage());
            }

            // Additional security validation for attribute access
            var attributeValidation = validateAttributeAccess(uriInfo.objectName(), uriInfo.attributeName());
            if (!attributeValidation.isValid()) {
                logger.warn("Attribute access validation failed for {}.{}: {}",
                    uriInfo.objectName(), uriInfo.attributeName(), attributeValidation.getErrorMessage());
                throw new SecurityException("Attribute access denied: " + attributeValidation.getErrorMessage());
            }

            // Log resource access if security logging is enabled
            if (properties.security().logOperations()) {
                logger.info("Resource access: {} -> {}.{}", uri, uriInfo.objectName(), uriInfo.attributeName());
            }

            // Get attribute value
            Object attributeValue = getAttributeValue(uriInfo.objectName(), uriInfo.attributeName());
            
            // Convert to JSON
            String jsonContent = objectMapper.writeValueAsString(Map.of(
                "objectName", uriInfo.objectName().toString(),
                "attributeName", uriInfo.attributeName(),
                "value", attributeValue,
                "type", attributeValue != null ? attributeValue.getClass().getSimpleName() : "null",
                TIMESTAMP_KEY, System.currentTimeMillis()
            ));

            var resourceContent = new McpSchema.TextResourceContents(uri, APPLICATION_JSON, jsonContent);
            return new McpSchema.ReadResourceResult(List.of(resourceContent));

        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            logger.error("Error serializing JMX resource {}: {}", uri, e.getMessage());

            // Return error as JSON content
            try {
                String errorJson = objectMapper.writeValueAsString(Map.of(
                    "error", e.getMessage(),
                    "uri", uri,
                    TIMESTAMP_KEY, System.currentTimeMillis()
                ));
                var errorContent = new McpSchema.TextResourceContents(uri, APPLICATION_JSON, errorJson);
                return new McpSchema.ReadResourceResult(List.of(errorContent));
            } catch (com.fasterxml.jackson.core.JsonProcessingException jsonError) {
                throw new JmxResourceException("Failed to serialize error response", jsonError);
            }
        } catch (RuntimeException e) {
            logger.error("Error reading JMX resource {}: {}", uri, e.getMessage());

            // Return error as JSON content
            try {
                String errorJson = objectMapper.writeValueAsString(Map.of(
                    "error", e.getMessage(),
                    "uri", uri,
                    TIMESTAMP_KEY, System.currentTimeMillis()
                ));
                var errorContent = new McpSchema.TextResourceContents(uri, APPLICATION_JSON, errorJson);
                return new McpSchema.ReadResourceResult(List.of(errorContent));
            } catch (com.fasterxml.jackson.core.JsonProcessingException jsonError) {
                throw new JmxResourceException("Failed to serialize error response", jsonError);
            }
        }
    }

    /**
     * Ensures that resources are discovered and cached.
     */
    private void ensureResourcesInitialized() {
        if (!resourcesInitialized) {
            synchronized (this) {
                if (!resourcesInitialized) {
                    discoverAndCacheResources();
                    resourcesInitialized = true;
                }
            }
        }
    }

    /**
     * Discovers JMX attributes and caches them as resource specifications.
     */
    private void discoverAndCacheResources() {
        logger.info("Discovering JMX attributes for MCP resources");

        resourceCacheLock.writeLock().lock();
        try {
            Set<String> oldResourceUris = new HashSet<>(resourceCache.keySet());
            Map<String, McpSchema.Resource> newResourceMap = buildNewResourceMap();

            ResourceChanges changes = detectResourceChanges(oldResourceUris, newResourceMap);
            updateResourceCache(newResourceMap);

            logger.info("Discovered {} JMX attribute resources from {} MBeans",
                newResourceMap.size(), discoveryService.getAllMBeans().size());

            notifyResourceChanges(changes);

        } finally {
            resourceCacheLock.writeLock().unlock();
        }
    }

    /**
     * Builds a new resource map from discovered MBeans.
     */
    private Map<String, McpSchema.Resource> buildNewResourceMap() {
        Map<String, McpSchema.Resource> newResourceMap = new HashMap<>();
        Collection<MBeanInfo> mbeans = discoveryService.getAllMBeans();

        for (MBeanInfo mbean : mbeans) {
            for (MBeanInfo.MBeanAttributeInfo attribute : mbean.attributes()) {
                if (shouldIncludeAttribute(attribute)) {
                    McpSchema.Resource resource = createResourceFromAttribute(mbean, attribute);
                    newResourceMap.put(resource.uri(), resource);
                }
            }
        }

        return newResourceMap;
    }

    /**
     * Detects changes between old and new resource sets.
     */
    private ResourceChanges detectResourceChanges(Set<String> oldResourceUris, Map<String, McpSchema.Resource> newResourceMap) {
        List<McpSchema.Resource> newResources = new ArrayList<>();
        List<String> removedResourceUris = new ArrayList<>();

        // Detect new resources
        for (Map.Entry<String, McpSchema.Resource> entry : newResourceMap.entrySet()) {
            if (!oldResourceUris.contains(entry.getKey())) {
                newResources.add(entry.getValue());
            }
        }

        // Detect removed resources
        for (String oldUri : oldResourceUris) {
            if (!newResourceMap.containsKey(oldUri)) {
                removedResourceUris.add(oldUri);
            }
        }

        return new ResourceChanges(newResources, removedResourceUris);
    }

    /**
     * Updates the resource cache with new resources.
     */
    private void updateResourceCache(Map<String, McpSchema.Resource> newResourceMap) {
        resourceCache.clear();
        resourceCache.putAll(newResourceMap);
    }

    /**
     * Notifies listeners about resource changes in separate threads.
     */
    private void notifyResourceChanges(ResourceChanges changes) {
        if (!changes.newResources().isEmpty()) {
            final List<McpSchema.Resource> finalNewResources = new ArrayList<>(changes.newResources());
            new Thread(() -> notifyResourcesAdded(finalNewResources)).start();
        }

        if (!changes.removedResourceUris().isEmpty()) {
            final List<String> finalRemovedUris = new ArrayList<>(changes.removedResourceUris());
            new Thread(() -> notifyResourcesRemoved(finalRemovedUris)).start();
        }
    }

    /**
     * Record for resource change information.
     */
    private record ResourceChanges(
        List<McpSchema.Resource> newResources,
        List<String> removedResourceUris
    ) {}

    /**
     * Creates an MCP resource from an MBean attribute.
     */
    private McpSchema.Resource createResourceFromAttribute(MBeanInfo mbean, MBeanInfo.MBeanAttributeInfo attribute) {
        String uri = mapper.createResourceUri(mbean.objectName(), attribute.name());
        String name = mapper.createResourceName(mbean.objectName(), attribute.name());
        String description = createAttributeDescription(mbean, attribute);

        // Create resource with proper constructor (uri, name, description, mimeType, annotations)
        return new McpSchema.Resource(uri, name, description, APPLICATION_JSON, null);
    }

    /**
     * Creates a description for an attribute resource.
     */
    private String createAttributeDescription(MBeanInfo mbean, MBeanInfo.MBeanAttributeInfo attribute) {
        StringBuilder desc = new StringBuilder();
        desc.append(String.format("JMX Attribute: %s.%s", mbean.objectName(), attribute.name()));
        
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

        if (!access.isEmpty()) {
            desc.append(String.format("%nAccess: %s", access));
        }
        
        return desc.toString();
    }

    /**
     * Checks if an attribute should be included as a resource.
     */
    private boolean shouldIncludeAttribute(MBeanInfo.MBeanAttributeInfo attribute) {
        // Check if attribute is excluded
        if (mapper.shouldExcludeAttribute(attribute.name())) {
            return false;
        }
        
        // Check access type inclusion
        return mapper.shouldIncludeAttribute(attribute);
    }

    /**
     * Parses a resource URI to extract ObjectName and attribute name.
     *
     * @param uri The resource URI in format: jmx://sanitizedObjectName/attributes/attributeName
     * @return ResourceUriInfo containing the ObjectName and attribute name, or null if invalid
     */
    private ResourceUriInfo parseResourceUri(String uri) {
        if (uri == null || uri.trim().isEmpty()) {
            logger.debug("URI is null or empty");
            return null;
        }

        Matcher matcher = RESOURCE_URI_PATTERN.matcher(uri);
        if (!matcher.matches()) {
            logger.debug("URI does not match expected pattern: {}", uri);
            return null;
        }

        String sanitizedObjectName = matcher.group(1);
        String attributeName = matcher.group(2);

        // Validate attribute name
        if (attributeName == null || attributeName.trim().isEmpty()) {
            logger.debug("Attribute name is null or empty in URI: {}", uri);
            return null;
        }

        // Find the actual ObjectName by searching through discovered MBeans
        // This is more reliable than trying to reverse the sanitization
        Collection<MBeanInfo> mbeans = discoveryService.getAllMBeans();
        for (MBeanInfo mbean : mbeans) {
            String sanitized = sanitizeObjectName(mbean.objectName());
            if (sanitized.equals(sanitizedObjectName)) {
                // Verify that the attribute exists on this MBean
                if (mbean.getAttribute(attributeName) != null) {
                    return new ResourceUriInfo(mbean.objectName(), attributeName);
                } else {
                    logger.debug("Attribute '{}' not found on MBean: {}", attributeName, mbean.objectName());
                    return null;
                }
            }
        }

        logger.debug("Could not find MBean for sanitized ObjectName: {}", sanitizedObjectName);
        return null;
    }

    /**
     * Validates a resource URI format without resolving it.
     *
     * @param uri The URI to validate
     * @return true if the URI has the correct format, false otherwise
     */
    public boolean isValidResourceUri(String uri) {
        if (uri == null || uri.trim().isEmpty()) {
            return false;
        }

        return RESOURCE_URI_PATTERN.matcher(uri).matches();
    }

    /**
     * Creates a resource URI for the given ObjectName and attribute name.
     * This is the inverse of parseResourceUri.
     *
     * @param objectName The MBean ObjectName
     * @param attributeName The attribute name
     * @return The formatted resource URI
     */
    public String createResourceUri(ObjectName objectName, String attributeName) {
        if (objectName == null || attributeName == null || attributeName.trim().isEmpty()) {
            throw new IllegalArgumentException("ObjectName and attributeName cannot be null or empty");
        }

        String sanitized = sanitizeObjectName(objectName);
        return String.format("jmx://%s/attributes/%s", sanitized, attributeName);
    }

    /**
     * Sanitizes ObjectName for use in URIs (same logic as JMXToMCPMapper).
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
     * Gets the value of a JMX attribute with basic caching support.
     */
    private Object getAttributeValue(ObjectName objectName, String attributeName) throws JmxResourceException {
        // Create cache key
        String cacheKey = objectName.toString() + "#" + attributeName;

        // Check cache first (with 30 second TTL)
        attributeCacheLock.readLock().lock();
        try {
            CachedAttributeValue cached = attributeValueCache.get(cacheKey);
            if (cached != null && !cached.isExpired()) {
                logger.debug("Returning cached value for {}.{}", objectName, attributeName);
                return cached.value();
            }
        } finally {
            attributeCacheLock.readLock().unlock();
        }

        // Get fresh value from JMX
        Object value;
        try {
            MBeanServerConnection connection = connectionManager.getConnection();
            value = connection.getAttribute(objectName, attributeName);
        } catch (javax.management.MBeanException | javax.management.AttributeNotFoundException |
                 javax.management.InstanceNotFoundException | javax.management.ReflectionException |
                 java.io.IOException | RuntimeException e) {
            throw new JmxResourceException("Failed to get attribute value for " + objectName + "." + attributeName, e);
        }

        // Check for value changes and notify listeners
        String resourceUri = createResourceUri(objectName, attributeName);
        Object oldValue = lastKnownValues.get(resourceUri);

        if (!Objects.equals(oldValue, value)) {
            // Value has changed, update cache and notify listeners
            lastKnownValues.put(resourceUri, value);

            if (oldValue != null) { // Don't notify on first read
                notifyResourceChanged(resourceUri, objectName, attributeName, oldValue, value);
            }
        }

        // Cache the value with 30 second TTL
        long ttl = 30000; // 30 seconds
        CachedAttributeValue cachedValue = new CachedAttributeValue(value, System.currentTimeMillis(), ttl);

        attributeCacheLock.writeLock().lock();
        try {
            attributeValueCache.put(cacheKey, cachedValue);

            // Clean up expired entries periodically
            if (attributeValueCache.size() % 100 == 0) {
                cleanupExpiredCacheEntries();
            }
        } finally {
            attributeCacheLock.writeLock().unlock();
        }

        return value;
    }

    /**
     * Cleans up expired cache entries.
     */
    private void cleanupExpiredCacheEntries() {
        attributeValueCache.entrySet().removeIf(entry -> entry.getValue().isExpired());
        logger.debug("Cleaned up expired cache entries. Current cache size: {}", attributeValueCache.size());
    }

    /**
     * Clears the attribute value cache.
     */
    public void clearAttributeCache() {
        attributeCacheLock.writeLock().lock();
        try {
            attributeValueCache.clear();
            logger.info("Cleared attribute value cache");
        } finally {
            attributeCacheLock.writeLock().unlock();
        }
    }

    /**
     * Gets cache statistics.
     */
    public CacheStats getCacheStats() {
        attributeCacheLock.readLock().lock();
        try {
            int totalEntries = attributeValueCache.size();
            long expiredEntries = attributeValueCache.values().stream()
                .mapToLong(cached -> cached.isExpired() ? 1 : 0)
                .sum();

            return new CacheStats(totalEntries, (int) expiredEntries, totalEntries - (int) expiredEntries);
        } finally {
            attributeCacheLock.readLock().unlock();
        }
    }

    /**
     * Record for cache statistics.
     */
    public record CacheStats(
        int totalEntries,
        int expiredEntries,
        int validEntries
    ) {}

    /**
     * Validates attribute access for security concerns.
     *
     * @param objectName The MBean ObjectName
     * @param attributeName The attribute name
     * @return ValidationResult indicating if access is allowed
     */
    private JmxSecurityValidator.ValidationResult validateAttributeAccess(ObjectName objectName, String attributeName) {
        // Validate attribute name for malicious patterns
        if (attributeName == null || attributeName.trim().isEmpty()) {
            return JmxSecurityValidator.ValidationResult.invalid("Attribute name cannot be null or empty");
        }

        // Check for potentially dangerous attribute names
        String lowerAttributeName = attributeName.toLowerCase();
        if (lowerAttributeName.contains("password") ||
            lowerAttributeName.contains("secret") ||
            lowerAttributeName.contains("key") ||
            lowerAttributeName.contains("token")) {
            logger.warn("Access to potentially sensitive attribute: {}.{}", objectName, attributeName);
            // Log but don't block - let the application decide
        }

        // Validate that the attribute exists and is readable
        try {
            Collection<MBeanInfo> mbeans = discoveryService.getAllMBeans();
            for (MBeanInfo mbean : mbeans) {
                if (mbean.objectName().equals(objectName)) {
                    MBeanInfo.MBeanAttributeInfo attribute = mbean.getAttribute(attributeName);
                    if (attribute == null) {
                        return JmxSecurityValidator.ValidationResult.invalid(
                            "Attribute '" + attributeName + "' not found on MBean: " + objectName);
                    }

                    if (!attribute.readable()) {
                        return JmxSecurityValidator.ValidationResult.invalid(
                            "Attribute '" + attributeName + "' is not readable on MBean: " + objectName);
                    }

                    return JmxSecurityValidator.ValidationResult.valid();
                }
            }

            return JmxSecurityValidator.ValidationResult.invalid("MBean not found: " + objectName);

        } catch (Exception e) {
            logger.error("Error validating attribute access for {}.{}", objectName, attributeName, e);
            return JmxSecurityValidator.ValidationResult.invalid("Error validating attribute access: " + e.getMessage());
        }
    }

    /**
     * Record for parsed resource URI information.
     */
    private record ResourceUriInfo(ObjectName objectName, String attributeName) {}

    /**
     * Refreshes the resource cache by rediscovering MBeans.
     * This method can be called when MBeans change.
     */
    public void refreshResources() {
        logger.info("Refreshing JMX resources cache");
        discoverAndCacheResources();
        resourcesInitialized = true;
    }

    /**
     * Gets the current number of cached resources.
     */
    public int getResourceCount() {
        resourceCacheLock.readLock().lock();
        try {
            return resourceCache.size();
        } finally {
            resourceCacheLock.readLock().unlock();
        }
    }

    /**
     * Scheduled method to refresh resources when MBeans change.
     * This integrates with the MBeanDiscoveryService refresh cycle.
     */
    @org.springframework.scheduling.annotation.Scheduled(
        fixedDelayString = "#{@jmxConnectionProperties.discovery().refreshInterval().toMillis() * 2}")
    public void scheduledResourceRefresh() {
        if (connectionManager.isConnected() && resourcesInitialized) {
            logger.debug("Performing scheduled resource refresh");
            refreshResources();
        }
    }

    /**
     * Gets resource statistics for monitoring.
     */
    public ResourceStats getResourceStats() {
        resourceCacheLock.readLock().lock();
        try {
            int totalResources = resourceCache.size();

            // Count resources by domain
            Map<String, Integer> resourcesByDomain = new HashMap<>();
            for (McpSchema.Resource resource : resourceCache.values()) {
                String uri = resource.uri();
                // Extract domain from URI: jmx://domain_type_property/attributes/attributeName
                if (uri.startsWith(JMX_URI_PREFIX)) {
                    String path = uri.substring(JMX_URI_PREFIX.length()); // Remove "jmx://"
                    int slashIndex = path.indexOf('/');
                    if (slashIndex > 0) {
                        String sanitizedObjectName = path.substring(0, slashIndex);
                        // Extract domain (first part before first underscore)
                        int underscoreIndex = sanitizedObjectName.indexOf('_');
                        String domain = underscoreIndex > 0 ?
                            sanitizedObjectName.substring(0, underscoreIndex) :
                            sanitizedObjectName;
                        resourcesByDomain.merge(domain, 1, Integer::sum);
                    }
                }
            }

            return new ResourceStats(totalResources, resourcesByDomain);
        } finally {
            resourceCacheLock.readLock().unlock();
        }
    }

    /**
     * Record for resource statistics.
     */
    public record ResourceStats(
        int totalResources,
        Map<String, Integer> resourcesByDomain
    ) {}

    /**
     * Record for cached attribute values with TTL.
     */
    private record CachedAttributeValue(
        Object value,
        long timestamp,
        long ttlMillis
    ) {
        public boolean isExpired() {
            return System.currentTimeMillis() - timestamp > ttlMillis;
        }
    }

    /**
     * Custom exception for JMX resource processing errors.
     */
    public static class JmxResourceException extends RuntimeException {
        public JmxResourceException(String message) {
            super(message);
        }

        public JmxResourceException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Interface for resource change listeners.
     */
    public interface ResourceChangeListener {
        /**
         * Called when a resource value changes.
         */
        void onResourceChanged(ResourceChangeEvent event);

        /**
         * Called when new resources are discovered.
         */
        void onResourcesAdded(List<McpSchema.Resource> newResources);

        /**
         * Called when resources are removed.
         */
        void onResourcesRemoved(List<String> removedResourceUris);
    }

    /**
     * Event representing a resource change.
     */
    public record ResourceChangeEvent(
        String resourceUri,
        ObjectName objectName,
        String attributeName,
        Object oldValue,
        Object newValue,
        long timestamp
    ) {}

    /**
     * Adds a resource change listener.
     */
    public void addResourceChangeListener(ResourceChangeListener listener) {
        if (listener != null) {
            changeListeners.add(listener);
            logger.debug("Added resource change listener: {}", listener.getClass().getSimpleName());
        }
    }

    /**
     * Removes a resource change listener.
     */
    public void removeResourceChangeListener(ResourceChangeListener listener) {
        if (listener != null) {
            changeListeners.remove(listener);
            logger.debug("Removed resource change listener: {}", listener.getClass().getSimpleName());
        }
    }

    /**
     * Notifies listeners about a resource value change.
     */
    private void notifyResourceChanged(String resourceUri, ObjectName objectName, String attributeName, Object oldValue, Object newValue) {
        if (changeListeners.isEmpty()) {
            return;
        }

        ResourceChangeEvent event = new ResourceChangeEvent(
            resourceUri, objectName, attributeName, oldValue, newValue, System.currentTimeMillis()
        );

        logger.debug("Resource changed: {} from {} to {}", resourceUri, oldValue, newValue);

        for (ResourceChangeListener listener : changeListeners) {
            try {
                listener.onResourceChanged(event);
            } catch (Exception e) {
                logger.error("Error notifying resource change listener", e);
            }
        }
    }

    /**
     * Notifies listeners about new resources being added.
     */
    private void notifyResourcesAdded(List<McpSchema.Resource> newResources) {
        if (changeListeners.isEmpty() || newResources.isEmpty()) {
            return;
        }

        logger.debug("New resources added: {}", newResources.size());

        for (ResourceChangeListener listener : changeListeners) {
            try {
                listener.onResourcesAdded(newResources);
            } catch (Exception e) {
                logger.error("Error notifying resource addition listener", e);
            }
        }
    }

    /**
     * Notifies listeners about resources being removed.
     */
    private void notifyResourcesRemoved(List<String> removedResourceUris) {
        if (changeListeners.isEmpty() || removedResourceUris.isEmpty()) {
            return;
        }

        logger.debug("Resources removed: {}", removedResourceUris.size());

        for (ResourceChangeListener listener : changeListeners) {
            try {
                listener.onResourcesRemoved(removedResourceUris);
            } catch (Exception e) {
                logger.error("Error notifying resource removal listener", e);
            }
        }
    }
}
