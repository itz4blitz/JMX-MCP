package org.jmxmcp.service;

import org.jmxmcp.config.JmxConnectionProperties;
import org.jmxmcp.config.JmxSecurityValidator;
import org.jmxmcp.jmx.JMXConnectionManager;
import org.jmxmcp.jmx.JMXToMCPMapper;
import org.jmxmcp.jmx.MBeanDiscoveryService;
import org.jmxmcp.model.MBeanInfo;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.server.McpServerFeatures;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for JmxResourceService.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JmxResourceServiceTest {

    @Mock
    private MBeanDiscoveryService discoveryService;
    
    @Mock
    private JMXConnectionManager connectionManager;
    
    @Mock
    private JmxSecurityValidator securityValidator;
    
    @Mock
    private JMXToMCPMapper mapper;
    
    @Mock
    private MBeanServerConnection mbeanConnection;

    private JmxConnectionProperties properties;
    private JmxResourceService resourceService;

    @BeforeEach
    @SuppressWarnings("unused") // Used by JUnit framework
    void setUp() {
        // Create default properties
        properties = new JmxConnectionProperties();
        
        // Create the service under test
        resourceService = new JmxResourceService(
            discoveryService, 
            connectionManager, 
            securityValidator, 
            mapper, 
            properties
        );
        
        // Setup common mock behaviors
        when(connectionManager.getConnection()).thenReturn(mbeanConnection);
        when(connectionManager.isConnected()).thenReturn(true);
    }

    @Test
    void testJmxResourcesWhenDisabled() {
        // Given: Resources are disabled
        var disabledProperties = new JmxConnectionProperties();
        disabledProperties.setResources(new JmxConnectionProperties.Resources(
            false, "jmx://", true, false, List.of()
        ));
        
        var disabledService = new JmxResourceService(
            discoveryService, connectionManager, securityValidator, mapper, disabledProperties
        );
        
        // When: Getting resources
        List<McpServerFeatures.SyncResourceSpecification> resources = disabledService.jmxResources();
        
        // Then: No resources should be returned
        assertTrue(resources.isEmpty());
    }

    @Test
    void testJmxResourcesWhenEnabled() throws Exception {
        // Given: Resources are enabled and MBeans are available
        ObjectName objectName = new ObjectName("java.lang:type=Memory");
        MBeanInfo.MBeanAttributeInfo attribute = new MBeanInfo.MBeanAttributeInfo(
            "HeapMemoryUsage", "javax.management.openmbean.CompositeData",
            "Heap memory usage", true, false, false
        );
        MBeanInfo mbeanInfo = new MBeanInfo(objectName, "java.lang.management.MemoryMXBean",
            "Memory management", List.of(attribute), List.of(), List.of());
        
        when(discoveryService.getAllMBeans()).thenReturn(List.of(mbeanInfo));
        when(mapper.createResourceUri(objectName, "HeapMemoryUsage"))
            .thenReturn("jmx://java.lang_type_Memory/attributes/HeapMemoryUsage");
        when(mapper.createResourceName(objectName, "HeapMemoryUsage"))
            .thenReturn("Memory.HeapMemoryUsage");
        when(mapper.shouldExcludeAttribute("HeapMemoryUsage")).thenReturn(false);
        when(mapper.shouldIncludeAttribute(attribute)).thenReturn(true);
        
        // When: Getting resources
        List<McpServerFeatures.SyncResourceSpecification> resources = resourceService.jmxResources();
        
        // Then: Resources should be returned
        assertFalse(resources.isEmpty());
        assertEquals(1, resources.size());
    }

    @Test
    void testReadResourceSuccess() throws Exception {
        // Given: Valid resource URI and successful attribute retrieval
        String uri = "jmx://java.lang_type_Memory/attributes/HeapMemoryUsage";
        ObjectName objectName = new ObjectName("java.lang:type=Memory");
        Object attributeValue = "512MB";
        
        MBeanInfo.MBeanAttributeInfo attribute = new MBeanInfo.MBeanAttributeInfo(
            "HeapMemoryUsage", "java.lang.String", "Heap memory usage", true, false, false
        );
        MBeanInfo mbeanInfo = new MBeanInfo(objectName, "java.lang.management.MemoryMXBean",
            "Memory management", List.of(attribute), List.of(), List.of());
        
        when(discoveryService.getAllMBeans()).thenReturn(List.of(mbeanInfo));
        when(securityValidator.validateObjectName(objectName))
            .thenReturn(JmxSecurityValidator.ValidationResult.valid());
        when(mbeanConnection.getAttribute(objectName, "HeapMemoryUsage"))
            .thenReturn(attributeValue);
        // Note: properties.security().logOperations() is a record method, not mockable
        
        McpSchema.ReadResourceRequest request = new McpSchema.ReadResourceRequest(uri);
        
        // When: Reading the resource
        McpSchema.ReadResourceResult result = invokeReadResource(request);
        
        // Then: Should return successful result
        assertNotNull(result);
        assertNotNull(result.contents());
        assertEquals(1, result.contents().size());
        
        McpSchema.TextResourceContents content = (McpSchema.TextResourceContents) result.contents().get(0);
        assertEquals(uri, content.uri());
        assertEquals("application/json", content.mimeType());
        assertTrue(content.text().contains("HeapMemoryUsage"));
        assertTrue(content.text().contains("512MB"));
    }

    @Test
    void testReadResourceInvalidUri() {
        // Given: Invalid URI format
        String invalidUri = "invalid://uri/format";
        McpSchema.ReadResourceRequest request = new McpSchema.ReadResourceRequest(invalidUri);
        
        // When: Reading the resource
        McpSchema.ReadResourceResult result = invokeReadResource(request);
        
        // Then: Should return error result
        assertNotNull(result);
        assertNotNull(result.contents());
        assertEquals(1, result.contents().size());
        
        McpSchema.TextResourceContents content = (McpSchema.TextResourceContents) result.contents().get(0);
        assertTrue(content.text().contains("error"));
    }

    @Test
    void testReadResourceSecurityValidationFailure() throws Exception {
        // Given: Security validation fails
        String uri = "jmx://java.lang_type_Memory/attributes/HeapMemoryUsage";
        ObjectName objectName = new ObjectName("java.lang:type=Memory");
        
        MBeanInfo.MBeanAttributeInfo attribute = new MBeanInfo.MBeanAttributeInfo(
            "HeapMemoryUsage", "java.lang.String", "Heap memory usage", true, false, false
        );
        MBeanInfo mbeanInfo = new MBeanInfo(objectName, "java.lang.management.MemoryMXBean",
            "Memory management", List.of(attribute), List.of(), List.of());
        
        when(discoveryService.getAllMBeans()).thenReturn(List.of(mbeanInfo));
        when(securityValidator.validateObjectName(objectName))
            .thenReturn(JmxSecurityValidator.ValidationResult.invalid("Security violation"));
        
        McpSchema.ReadResourceRequest request = new McpSchema.ReadResourceRequest(uri);
        
        // When: Reading the resource
        McpSchema.ReadResourceResult result = invokeReadResource(request);
        
        // Then: Should return error result
        assertNotNull(result);
        assertNotNull(result.contents());
        assertEquals(1, result.contents().size());
        
        McpSchema.TextResourceContents content = (McpSchema.TextResourceContents) result.contents().get(0);
        assertTrue(content.text().contains("error"));
        assertTrue(content.text().contains("Security violation"));
    }

    @Test
    void testIsValidResourceUri() {
        // Test valid URIs
        assertTrue(resourceService.isValidResourceUri("jmx://java.lang_type_Memory/attributes/HeapMemoryUsage"));
        assertTrue(resourceService.isValidResourceUri("jmx://com.example_type_Test/attributes/Value"));
        
        // Test invalid URIs
        assertFalse(resourceService.isValidResourceUri(null));
        assertFalse(resourceService.isValidResourceUri(""));
        assertFalse(resourceService.isValidResourceUri("invalid://uri"));
        assertFalse(resourceService.isValidResourceUri("jmx://missing/path"));
        assertFalse(resourceService.isValidResourceUri("jmx://domain/wrong/path"));
    }

    @Test
    void testCreateResourceUri() throws Exception {
        // Given: ObjectName and attribute name
        ObjectName objectName = new ObjectName("java.lang:type=Memory");
        String attributeName = "HeapMemoryUsage";
        
        // When: Creating resource URI
        String uri = resourceService.createResourceUri(objectName, attributeName);
        
        // Then: Should return properly formatted URI
        assertNotNull(uri);
        assertTrue(uri.startsWith("jmx://"));
        assertTrue(uri.contains("/attributes/"));
        assertTrue(uri.endsWith("HeapMemoryUsage"));
    }

    @Test
    void testCreateResourceUriWithNullParameters() {
        // Test null ObjectName
        IllegalArgumentException exception1 = assertThrows(IllegalArgumentException.class, () ->
            resourceService.createResourceUri(null, "attribute"));
        assertNotNull(exception1.getMessage());

        // Test null attribute name
        try {
            ObjectName testObjectName = new ObjectName("java.lang:type=Test");
            IllegalArgumentException exception2 = assertThrows(IllegalArgumentException.class, () ->
                resourceService.createResourceUri(testObjectName, null));
            assertNotNull(exception2.getMessage());
        } catch (javax.management.MalformedObjectNameException e) {
            fail("Failed to create test ObjectName: " + e.getMessage());
        }

        // Test empty attribute name
        try {
            ObjectName testObjectName = new ObjectName("java.lang:type=Test");
            IllegalArgumentException exception3 = assertThrows(IllegalArgumentException.class, () ->
                resourceService.createResourceUri(testObjectName, ""));
            assertNotNull(exception3.getMessage());
        } catch (javax.management.MalformedObjectNameException e) {
            fail("Failed to create test ObjectName: " + e.getMessage());
        }
    }

    @Test
    void testResourceChangeNotification() {
        // Given: Resource change listener
        TestResourceChangeListener listener = new TestResourceChangeListener();
        resourceService.addResourceChangeListener(listener);
        
        // When: Resource changes (this would happen during attribute value retrieval)
        // We can't easily test this without exposing internal methods, so we test the listener management
        
        // Then: Listener should be added
        resourceService.removeResourceChangeListener(listener);
        
        // Verify no exceptions are thrown
        assertDoesNotThrow(() -> {
            resourceService.addResourceChangeListener(null);
            resourceService.removeResourceChangeListener(null);
        });
    }

    @Test
    void testGetResourceCount() {
        // Initially should be 0
        assertEquals(0, resourceService.getResourceCount());
    }

    @Test
    void testRefreshResources() {
        // Should not throw exception
        assertDoesNotThrow(() -> resourceService.refreshResources());
    }

    // Helper method to invoke the private readResource method
    private McpSchema.ReadResourceResult invokeReadResource(McpSchema.ReadResourceRequest request) {
        try {
            var method = JmxResourceService.class.getDeclaredMethod(
                "readResource", Object.class, McpSchema.ReadResourceRequest.class
            );
            method.setAccessible(true);
            return (McpSchema.ReadResourceResult) method.invoke(resourceService, null, request);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("Failed to find readResource method", e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Failed to access readResource method", e);
        } catch (java.lang.reflect.InvocationTargetException e) {
            throw new RuntimeException("Failed to invoke readResource method", e);
        }
    }

    // Test implementation of ResourceChangeListener
    private static class TestResourceChangeListener implements JmxResourceService.ResourceChangeListener {
        @Override
        public void onResourceChanged(JmxResourceService.ResourceChangeEvent event) {
            // Test implementation
        }

        @Override
        public void onResourcesAdded(List<McpSchema.Resource> newResources) {
            // Test implementation
        }

        @Override
        public void onResourcesRemoved(List<String> removedResourceUris) {
            // Test implementation
        }
    }
}
