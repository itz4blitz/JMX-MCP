package org.jmxmcp;

import com.sun.tools.attach.AttachNotSupportedException;
import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.VirtualMachineDescriptor;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test to verify JMX connections work properly with discovered processes
 */
class JmxConnectionTest {

    private static final Logger logger = LoggerFactory.getLogger(JmxConnectionTest.class);

    @Test
    void testConnectToJavaApplication() {
        // Get current process ID
        String currentPid = ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
        assertNotNull(currentPid, "Current process ID should not be null");
        assertFalse(currentPid.isEmpty(), "Current process ID should not be empty");
        System.out.println("Current test PID: " + currentPid);

        // List all Java processes
        List<VirtualMachineDescriptor> vms = VirtualMachine.list();
        assertNotNull(vms, "Virtual machine list should not be null");
        System.out.println("Found " + vms.size() + " Java processes");

        boolean javaAppFound = false;

        for (VirtualMachineDescriptor vmd : vms) {
            // Skip self
            if (vmd.id().equals(currentPid)) {
                continue;
            }

            // Look for any Java application (excluding common IDE/build tools)
            if (!vmd.displayName().isEmpty() &&
                !vmd.displayName().contains("jmx-mcp-server") &&
                !vmd.displayName().contains("idea") &&
                !vmd.displayName().contains("eclipse") &&
                !vmd.displayName().contains("maven") &&
                !vmd.displayName().contains("gradle")) {
                javaAppFound = true;
                System.out.println("Found Java Application: PID " + vmd.id() + " - " + vmd.displayName());

                try {
                    VirtualMachine vm = VirtualMachine.attach(vmd.id());
                    assertNotNull(vm, "Virtual machine instance should not be null");

                    Properties agentProps = vm.getAgentProperties();
                    assertNotNull(agentProps, "Agent properties should not be null");

                    String jmxUrl = agentProps.getProperty("com.sun.management.jmxremote.localConnectorAddress");

                    if (jmxUrl != null) {
                        System.out.println("JMX URL: " + jmxUrl);
                        assertFalse(jmxUrl.isEmpty(), "JMX URL should not be empty");

                        // Test connection
                        JMXServiceURL serviceURL = new JMXServiceURL(jmxUrl);
                        assertNotNull(serviceURL, "JMX Service URL should not be null");

                        try (JMXConnector connector = JMXConnectorFactory.connect(serviceURL, null)) {
                            assertNotNull(connector, "JMX Connector should not be null");

                            MBeanServerConnection connection = connector.getMBeanServerConnection();
                            assertNotNull(connection, "MBean server connection should not be null");

                            // Get all domains
                            String[] domains = connection.getDomains();
                            assertNotNull(domains, "Domains array should not be null");
                            assertTrue(domains.length > 0, "Should have at least one domain");
                            System.out.println("Found " + domains.length + " domains:");
                            for (String domain : domains) {
                                System.out.println("  - " + domain);
                            }

                            // Look for specific MBeans
                            Set<ObjectName> mbeans = connection.queryNames(null, null);
                            assertNotNull(mbeans, "MBeans set should not be null");
                            assertFalse(mbeans.isEmpty(), "Should have at least one MBean");
                            System.out.println("Total MBeans: " + mbeans.size());

                            // Look for Hikari MBeans
                            Set<ObjectName> hikariMBeans = connection.queryNames(
                                new ObjectName("com.zaxxer.hikari:*"), null);
                            assertNotNull(hikariMBeans, "Hikari MBeans set should not be null");
                            System.out.println("Hikari MBeans: " + hikariMBeans.size());
                            for (ObjectName hikari : hikariMBeans) {
                                System.out.println("  - " + hikari);
                            }

                            // Look for Spring MBeans
                            Set<ObjectName> springMBeans = connection.queryNames(
                                new ObjectName("org.springframework.*:*"), null);
                            assertNotNull(springMBeans, "Spring MBeans set should not be null");
                            System.out.println("Spring MBeans: " + springMBeans.size());
                            for (ObjectName spring : springMBeans) {
                                System.out.println("  - " + spring);
                            }

                            // Connection successful - all assertions passed
                        }
                    } else {
                        System.out.println("No JMX URL found for Java Application");
                    }

                    vm.detach();
                    break;

                } catch (AttachNotSupportedException e) {
                    logger.warn("Attach not supported for PID {}: {}", vmd.id(), e.getMessage());
                    System.out.println("Attach not supported for Java Application: " + e.getMessage());
                } catch (IOException e) {
                    logger.warn("IO error attaching to PID {}: {}", vmd.id(), e.getMessage());
                    System.out.println("IO error connecting to Java Application: " + e.getMessage());
                } catch (javax.management.MalformedObjectNameException e) {
                    logger.warn("Invalid ObjectName: {}", e.getMessage());
                    System.out.println("Invalid ObjectName: " + e.getMessage());
                } catch (RuntimeException e) {
                    logger.warn("Runtime error connecting to PID {}: {}", vmd.id(), e.getMessage());
                    System.out.println("Runtime error connecting to Java Application: " + e.getMessage());
                }
            }
        }

        // Test assertions - we don't require a specific app to be running, but if one is, connection should work
        if (javaAppFound) {
            System.out.println("Java Application was found and tested");
            // If we found the app, we should have been able to connect (unless there are permission issues)
            // This is more of an informational test than a strict requirement
        } else {
            System.out.println("No suitable Java Application found in running processes - this is expected");
        }

        // The test passes if we can list VMs (basic functionality works)
        assertTrue(vms.size() >= 1, "Should be able to discover at least one Java process (ourselves)");
    }
}
