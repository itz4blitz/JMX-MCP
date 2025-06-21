package org.jmxmcp.service;

import org.jmxmcp.config.JmxConnectionProperties;
import org.jmxmcp.config.JmxSecurityValidator;
import org.jmxmcp.jmx.JMXConnectionManager;
import org.jmxmcp.jmx.MBeanDiscoveryService;
import org.jmxmcp.model.MBeanInfo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JmxServiceTest {

    @Mock
    private MBeanDiscoveryService discoveryService;

    @Mock
    private JMXConnectionManager connectionManager;

    @Mock
    private JmxSecurityValidator securityValidator;

    @Mock
    private JmxDiscoveryService jmxDiscoveryService;

    private JmxService jmxService;

    // JUnit calls @BeforeEach methods via reflection
    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        jmxService = new JmxService(discoveryService, connectionManager, securityValidator, jmxDiscoveryService);
    }

    @Test
    void listMBeans_withNoDomain_shouldReturnAllMBeans() {
        // Given
        MBeanInfo mockMBean = createMockMBeanInfo("java.lang:type=Memory");
        when(discoveryService.getAllMBeans()).thenReturn(List.of(mockMBean));

        // When
        String result = jmxService.listMBeans(null);

        // Then
        assertThat(result)
            .contains("Found 1 MBeans")
            .contains("java.lang:type=Memory")
            .contains("TestClass");
    }

    @Test
    void listMBeans_withDomain_shouldReturnFilteredMBeans() {
        // Given
        MBeanInfo mockMBean = createMockMBeanInfo("java.lang:type=Memory");
        when(discoveryService.getMBeansByDomain("java.lang")).thenReturn(List.of(mockMBean));

        // When
        String result = jmxService.listMBeans("java.lang");

        // Then
        assertThat(result)
            .contains("Found 1 MBeans")
            .contains("in domain 'java.lang'")
            .contains("java.lang:type=Memory");
    }

    @Test
    void listMBeans_withEmptyResult_shouldReturnNoMBeansMessage() {
        // Given
        when(discoveryService.getAllMBeans()).thenReturn(List.of());

        // When
        String result = jmxService.listMBeans(null);

        // Then
        assertThat(result).isEqualTo("No MBeans discovered");
    }

    @Test
    void getMBeanInfo_withValidObjectName_shouldReturnMBeanDetails() {
        // Given
        String objectName = "java.lang:type=Memory";
        MBeanInfo mockMBean = createMockMBeanInfo(objectName);
        when(discoveryService.getMBean(objectName)).thenReturn(Optional.of(mockMBean));

        // When
        String result = jmxService.getMBeanInfo(objectName);

        // Then
        assertThat(result)
            .contains("MBean Information:")
            .contains("ObjectName: java.lang:type=Memory")
            .contains("Class: TestClass")
            .contains("Attributes (1):")
            .contains("Operations (1):");
    }

    @Test
    void getMBeanInfo_withInvalidObjectName_shouldReturnNotFoundMessage() {
        // Given
        String objectName = "invalid:name=test";
        when(discoveryService.getMBean(objectName)).thenReturn(Optional.empty());

        // When
        String result = jmxService.getMBeanInfo(objectName);

        // Then
        assertThat(result).isEqualTo("MBean not found: invalid:name=test");
    }

    @Test
    void listDomains_shouldReturnAllDomains() {
        // Given
        Set<String> domains = Set.of("java.lang", "com.sun.management", "java.util.logging");
        when(discoveryService.getAllDomains()).thenReturn(domains);

        // When
        String result = jmxService.listDomains();

        // Then
        assertThat(result)
            .contains("MBean Domains (3):")
            .contains("java.lang")
            .contains("com.sun.management")
            .contains("java.util.logging");
    }

    @Test
    void getConnectionInfo_shouldReturnConnectionDetails() {
        // Given
        JMXConnectionManager.ConnectionInfo connectionInfo =
            new JMXConnectionManager.ConnectionInfo(JmxConnectionProperties.ConnectionType.LOCAL, null, true, 25);
        MBeanDiscoveryService.DiscoveryStats stats =
            new MBeanDiscoveryService.DiscoveryStats(25, 150, 75, 5);
        
        when(connectionManager.getConnectionInfo()).thenReturn(connectionInfo);
        when(discoveryService.getDiscoveryStats()).thenReturn(stats);

        // When
        String result = jmxService.getConnectionInfo();

        // Then
        assertThat(result)
            .contains("JMX Connection Information:")
            .contains("Connection Type: LOCAL")
            .contains("Connected: Yes")
            .contains("Total MBeans: 25")
            .contains("Total Attributes: 150")
            .contains("Total Operations: 75")
            .contains("Total Domains: 5");
    }

    private MBeanInfo createMockMBeanInfo(String objectNameStr) {
        try {
            ObjectName objectName = new ObjectName(objectNameStr);
            
            MBeanInfo.MBeanAttributeInfo attr = new MBeanInfo.MBeanAttributeInfo(
                "TestAttribute", "java.lang.String", "Test attribute", true, false, false
            );

            MBeanInfo.MBeanOperationInfo op = new MBeanInfo.MBeanOperationInfo(
                "testOperation", "Test operation", "void", List.of(), 1
            );

            return new MBeanInfo(
                objectName,
                "TestClass",
                "Test MBean",
                List.of(attr),
                List.of(op),
                List.of()
            );
        } catch (MalformedObjectNameException e) {
            throw new RuntimeException(e);
        }
    }
}
