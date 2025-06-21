package org.jmxmcp;

import com.sun.tools.attach.AttachNotSupportedException;
import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.VirtualMachineDescriptor;

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

/**
 * Demonstration of how to connect to Java applications via JMX
 * and explore their MBeans including Hikari, Spring Boot, etc.
 */
public class DemoJavaConnection {

    public static void main(String[] args) {
        try {
            System.out.println("=== Demonstrating JMX Connection to Java Applications ===\n");

            String currentPid = ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
            System.out.println("Current demo PID: " + currentPid);

            List<VirtualMachineDescriptor> vms = VirtualMachine.list();
            System.out.println("Scanning " + vms.size() + " Java processes...\n");

            for (VirtualMachineDescriptor vmd : vms) {
                // Skip self
                if (vmd.id().equals(currentPid)) {
                    continue;
                }

                // Look for interesting Java applications (skip common tools)
                if (!vmd.displayName().isEmpty() &&
                    !vmd.displayName().contains("jmx-mcp-server") &&
                    !vmd.displayName().contains("idea") &&
                    !vmd.displayName().contains("eclipse") &&
                    !vmd.displayName().contains("maven") &&
                    !vmd.displayName().contains("gradle")) {
                    System.out.println("🎯 FOUND Java Application!");
                    System.out.println("   PID: " + vmd.id());
                    System.out.println("   Display Name: " + vmd.displayName());
                    
                    VirtualMachine vm = VirtualMachine.attach(vmd.id());
                    Properties agentProps = vm.getAgentProperties();
                    String jmxUrl = agentProps.getProperty("com.sun.management.jmxremote.localConnectorAddress");
                    
                    if (jmxUrl != null) {
                        System.out.println("   JMX URL: " + jmxUrl);
                        System.out.println("\n🔗 CONNECTING...");
                        
                        // Test connection
                        JMXServiceURL serviceURL = new JMXServiceURL(jmxUrl);
                        try (JMXConnector connector = JMXConnectorFactory.connect(serviceURL, null)) {
                            MBeanServerConnection connection = connector.getMBeanServerConnection();
                            
                            System.out.println("✅ CONNECTION SUCCESSFUL!\n");
                            
                            // Get all domains
                            String[] domains = connection.getDomains();
                            System.out.println("📊 DOMAINS FOUND (" + domains.length + "):");
                            for (String domain : domains) {
                                System.out.println("   • " + domain);
                            }
                            
                            // Get total MBean count
                            Set<ObjectName> allMBeans = connection.queryNames(null, null);
                            System.out.println("\n📈 TOTAL MBEANS: " + allMBeans.size());
                            
                            // Look for Hikari MBeans (what you see in VisualVM)
                            System.out.println("\n🏊 HIKARI CONNECTION POOL MBEANS:");
                            Set<ObjectName> hikariMBeans = connection.queryNames(
                                new ObjectName("com.zaxxer.hikari:*"), null);
                            if (hikariMBeans.isEmpty()) {
                                System.out.println("   (None found)");
                            } else {
                                for (ObjectName hikari : hikariMBeans) {
                                    System.out.println("   ✅ " + hikari);
                                    
                                    // Get some attributes
                                    try {
                                        Object activeConnections = connection.getAttribute(hikari, "ActiveConnections");
                                        Object totalConnections = connection.getAttribute(hikari, "TotalConnections");
                                        System.out.println("      Active: " + activeConnections + ", Total: " + totalConnections);
                                    } catch (javax.management.MBeanException | javax.management.AttributeNotFoundException |
                                             javax.management.InstanceNotFoundException | javax.management.ReflectionException e) {
                                        System.out.println("      (JMX error reading attributes: " + e.getMessage() + ")");
                                    } catch (java.io.IOException e) {
                                        System.out.println("      (Connection error reading attributes: " + e.getMessage() + ")");
                                    } catch (RuntimeException e) {
                                        System.out.println("      (Runtime error reading attributes: " + e.getMessage() + ")");
                                    }
                                }
                            }
                            
                            // Look for Spring Boot MBeans
                            System.out.println("\n🌱 SPRING BOOT MBEANS:");
                            Set<ObjectName> springMBeans = connection.queryNames(
                                new ObjectName("org.springframework.*:*"), null);
                            if (springMBeans.isEmpty()) {
                                System.out.println("   (None found)");
                            } else {
                                int count = 0;
                                for (ObjectName spring : springMBeans) {
                                    System.out.println("   ✅ " + spring);
                                    count++;
                                    if (count >= 5) {
                                        System.out.println("   ... and " + (springMBeans.size() - 5) + " more");
                                        break;
                                    }
                                }
                            }
                            
                            // Look for AWS MBeans
                            System.out.println("\n☁️ AWS MANAGEMENT MBEANS:");
                            Set<ObjectName> awsMBeans = connection.queryNames(
                                new ObjectName("com.amazonaws.*:*"), null);
                            if (awsMBeans.isEmpty()) {
                                System.out.println("   (None found)");
                            } else {
                                for (ObjectName aws : awsMBeans) {
                                    System.out.println("   ✅ " + aws);
                                }
                            }
                            
                            // Look for application-specific MBeans
                            System.out.println("\n🏢 APPLICATION-SPECIFIC MBEANS:");
                            Set<ObjectName> appMBeans = connection.queryNames(
                                new ObjectName("com.*:*"), null);
                            // Filter out common framework MBeans to show only app-specific ones
                            appMBeans.removeIf(name ->
                                name.getDomain().startsWith("com.zaxxer") ||
                                name.getDomain().startsWith("com.amazonaws") ||
                                name.getDomain().startsWith("com.sun"));

                            if (appMBeans.isEmpty()) {
                                System.out.println("   (None found - application might not expose custom MBeans)");
                            } else {
                                for (ObjectName app : appMBeans) {
                                    System.out.println("   ✅ " + app);
                                }
                            }

                            System.out.println("\n🎉 DEMONSTRATION COMPLETE!");
                            System.out.println("✅ Successfully connected to Java Application");
                            System.out.println("✅ Found " + domains.length + " domains and " + allMBeans.size() + " MBeans");
                            System.out.println("✅ This proves the JMX MCP server CAN access Java applications!");
                        }
                    } else {
                        System.out.println("   ❌ No JMX URL found - JMX might not be enabled");
                    }
                    
                    vm.detach();
                    return; // Found and processed Java app
                }
            }

            System.out.println("❌ No suitable Java Application found in running processes");
            System.out.println("Try running a Spring Boot application or other Java application with JMX enabled");
            
        } catch (AttachNotSupportedException e) {
            System.err.println("❌ Demo failed: Attach API not supported - " + e.getMessage());
            System.err.println("   This might happen on some JVM configurations or operating systems");
        } catch (IOException e) {
            System.err.println("❌ Demo failed: I/O error - " + e.getMessage());
            System.err.println("   Check if the target process is accessible and JMX is enabled");
        } catch (javax.management.MalformedObjectNameException e) {
            System.err.println("❌ Demo failed: Invalid ObjectName - " + e.getMessage());
        } catch (RuntimeException e) {
            System.err.println("❌ Demo failed: Runtime error - " + e.getMessage());
            System.err.println("   Error type: " + e.getClass().getSimpleName());
        }
    }
}
