package org.jmxmcp;

import com.sun.tools.attach.AttachNotSupportedException;
import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.VirtualMachineDescriptor;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test to verify Attach API functionality
 */
class AttachApiTest {

    private static final Logger logger = LoggerFactory.getLogger(AttachApiTest.class);

    @Test
    void testAttachApiAvailable() {
        // Get current process ID
        String currentPid = ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
        assertNotNull(currentPid, "Current process ID should not be null");
        assertFalse(currentPid.isEmpty(), "Current process ID should not be empty");
        System.out.println("Current process PID: " + currentPid);

        // List all Java processes
        List<VirtualMachineDescriptor> vms = VirtualMachine.list();
        assertNotNull(vms, "Virtual machine list should not be null");
        System.out.println("Found " + vms.size() + " Java processes:");

        // At least our own process should be in the list
        assertTrue(vms.size() >= 1, "Should find at least one Java process (ourselves)");

        int attachAttempts = 0;
        int successfulAttachments = 0;

        for (VirtualMachineDescriptor vmd : vms) {
            System.out.println("  PID: " + vmd.id() + " - " + vmd.displayName());

            // Skip self
            if (vmd.id().equals(currentPid)) {
                System.out.println("    (Skipping self)");
                continue;
            }

            attachAttempts++;

            // Try to attach
            try {
                VirtualMachine vm = VirtualMachine.attach(vmd.id());
                assertNotNull(vm, "Virtual machine instance should not be null");

                Properties agentProps = vm.getAgentProperties();
                assertNotNull(agentProps, "Agent properties should not be null");

                String jmxUrl = agentProps.getProperty("com.sun.management.jmxremote.localConnectorAddress");

                if (jmxUrl != null) {
                    System.out.println("    JMX URL: " + jmxUrl);
                    successfulAttachments++;
                } else {
                    System.out.println("    No JMX URL found, trying to start management agent...");
                    try {
                        vm.startLocalManagementAgent();
                        agentProps = vm.getAgentProperties();
                        jmxUrl = agentProps.getProperty("com.sun.management.jmxremote.localConnectorAddress");
                        if (jmxUrl != null) {
                            System.out.println("    JMX URL after starting agent: " + jmxUrl);
                            successfulAttachments++;
                        } else {
                            System.out.println("    Could not start management agent");
                        }
                    } catch (IOException e) {
                        System.out.println("    Failed to start management agent: " + e.getMessage());
                        logger.debug("Management agent start failed for PID {}: {}", vmd.id(), e.getMessage());
                    } catch (RuntimeException e) {
                        System.out.println("    Runtime error starting management agent: " + e.getMessage());
                        logger.debug("Runtime error starting management agent for PID {}: {}", vmd.id(), e.getMessage());
                    }
                }

                vm.detach();

            } catch (AttachNotSupportedException e) {
                System.out.println("    Attach not supported: " + e.getMessage());
                logger.debug("Attach not supported for PID {}: {}", vmd.id(), e.getMessage());
            } catch (IOException e) {
                System.out.println("    IO error during attach: " + e.getMessage());
                logger.debug("IO error attaching to PID {}: {}", vmd.id(), e.getMessage());
            } catch (RuntimeException e) {
                System.out.println("    Runtime error during attach: " + e.getMessage());
                logger.debug("Runtime error attaching to PID {}: {}", vmd.id(), e.getMessage());
            }
        }

        // Verify that the Attach API is working
        System.out.println("Attach attempts: " + attachAttempts + ", Successful: " + successfulAttachments);

        // The test passes if we can list VMs (Attach API is available)
        // We don't require successful attachments since other processes might not allow it
        assertNotNull(vms, "Virtual machine list should be accessible");
        assertFalse(vms.isEmpty(), "Should be able to discover at least one Java process");
    }
}
