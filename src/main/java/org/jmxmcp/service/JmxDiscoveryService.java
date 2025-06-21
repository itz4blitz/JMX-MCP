package org.jmxmcp.service;

import org.jmxmcp.jmx.JmxConnectionInfo;
import org.jmxmcp.jmx.JmxConnectionRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.management.MBeanServerConnection;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.net.ConnectException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.ExecutionException;

// Attach API imports
import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.VirtualMachineDescriptor;
import com.sun.tools.attach.AttachNotSupportedException;

/**
 * Service for discovering JMX-enabled applications on the local system.
 * 
 * This service can:
 * - Scan common JMX ports for remote connections
 * - Discover local Java processes with JMX enabled
 * - Test connections and gather metadata
 * - Auto-register discovered connections
 */
@Service
public class JmxDiscoveryService {
    
    private static final Logger logger = LoggerFactory.getLogger(JmxDiscoveryService.class);
    
    // Common JMX ports to scan
    private static final int[] DEFAULT_SCAN_PORTS = {
        9999, 9010, 8999, 7199, // Common application ports
        1099, 1098, 1097,       // RMI registry ports
        8080, 8081, 8082,       // Web application management ports
        9990, 9991, 9992        // JBoss/WildFly management ports
    };
    
    private final JmxConnectionRegistry connectionRegistry;
    private final ExecutorService executorService;

    public JmxDiscoveryService(JmxConnectionRegistry connectionRegistry) {
        this.connectionRegistry = connectionRegistry;
        this.executorService = Executors.newFixedThreadPool(5);
    }
    
    /**
     * Discovers JMX services on the local system
     */
    public DiscoveryResult discoverJmxServices() {
        logger.info("Starting JMX service discovery...");
        
        List<JmxConnectionInfo> discovered = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        
        try {
            // Scan common ports for JMX services
            List<JmxConnectionInfo> portScanResults = scanCommonPorts();
            discovered.addAll(portScanResults);
            
            // Try to discover local Java processes (if available)
            discoverAndAddLocalProcesses(discovered, errors);
            
        } catch (Exception e) {
            logger.error("Discovery failed", e);
            errors.add("Discovery failed: " + e.getMessage());
        }
        
        logger.info("Discovery completed. Found {} JMX services", discovered.size());
        return new DiscoveryResult(discovered, errors);
    }
    
    /**
     * Discovers and adds local processes to the discovered list
     */
    private void discoverAndAddLocalProcesses(List<JmxConnectionInfo> discovered, List<String> errors) {
        try {
            List<JmxConnectionInfo> processResults = discoverLocalProcesses();
            discovered.addAll(processResults);
        } catch (Exception e) {
            logger.warn("Local process discovery failed: {}", e.getMessage());
            errors.add("Local process discovery failed: " + e.getMessage());
        }
    }

    /**
     * Scans common ports for JMX services
     */
    private List<JmxConnectionInfo> scanCommonPorts() {
        logger.info("Scanning common JMX ports...");
        
        List<CompletableFuture<Optional<JmxConnectionInfo>>> futures = new ArrayList<>();
        
        for (int port : DEFAULT_SCAN_PORTS) {
            CompletableFuture<Optional<JmxConnectionInfo>> future = CompletableFuture.supplyAsync(() ->
                testJmxConnection("localhost", port), executorService);
            futures.add(future);
        }
        
        List<JmxConnectionInfo> results = new ArrayList<>();
        for (CompletableFuture<Optional<JmxConnectionInfo>> future : futures) {
            try {
                Optional<JmxConnectionInfo> result = future.get(5, TimeUnit.SECONDS);
                result.ifPresent(results::add);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.debug("Port scan interrupted: {}", e.getMessage());
            } catch (ExecutionException | TimeoutException e) {
                logger.debug("Port scan future failed: {}", e.getMessage());
            }
        }
        
        logger.info("Port scan completed. Found {} services", results.size());
        return results;
    }
    
    /**
     * Tests a JMX connection to a specific host and port
     */
    private Optional<JmxConnectionInfo> testJmxConnection(String host, int port) {
        String url = String.format("service:jmx:rmi:///jndi/rmi://%s:%d/jmxrmi", host, port);
        String connectionId = String.format("discovered-%s-%d", host, port);
        String connectionName = String.format("Discovered JMX (%s:%d)", host, port);
        
        try {
            logger.debug("Testing JMX connection: {}", url);
            
            JMXServiceURL serviceURL = new JMXServiceURL(url);
            Map<String, Object> environment = new HashMap<>();
            
            // Set short timeout for discovery
            environment.put("jmx.remote.x.request.waiting.timeout", "3000");
            environment.put("jmx.remote.x.notification.fetch.timeout", "3000");
            
            try (JMXConnector connector = JMXConnectorFactory.connect(serviceURL, environment)) {
                MBeanServerConnection connection = connector.getMBeanServerConnection();

                // Get basic information about the connection
                int mbeanCount = connection.getMBeanCount();
                String applicationName = getApplicationName(connection);

                // Create connection info
                String finalName = applicationName != null ?
                    String.format("%s (%s:%d)", applicationName, host, port) : connectionName;

                JmxConnectionInfo connectionInfo = JmxConnectionInfo.createRemote(
                    connectionId, finalName, url, null, null, Map.of());

                logger.info("Discovered JMX service: {} with {} MBeans", finalName, mbeanCount);
                return Optional.of(connectionInfo.withConnection(null, null,
                    JmxConnectionInfo.ConnectionStatus.DISCONNECTED, mbeanCount));
            }
            
        } catch (ConnectException e) {
            logger.debug("No JMX service at {}:{}", host, port);
        } catch (java.io.IOException e) {
            logger.debug("Failed to connect to {}:{}: {}", host, port, e.getMessage());
        }
        
        return Optional.empty();
    }
    
    /**
     * Attempts to get the application name from the JMX connection
     */
    private String getApplicationName(MBeanServerConnection connection) {
        try {
            // Try to get the application name from Runtime MBean
            RuntimeMXBean runtimeBean = ManagementFactory.newPlatformMXBeanProxy(
                connection, ManagementFactory.RUNTIME_MXBEAN_NAME, RuntimeMXBean.class);
            
            String name = runtimeBean.getName();
            if (name != null && name.contains("@")) {
                // Extract process name if available
                String[] parts = name.split("@");
                if (parts.length > 0) {
                    return "Java Process " + parts[0];
                }
            }
            
            // Try to get system properties for more info
            Map<String, String> systemProperties = runtimeBean.getSystemProperties();
            String mainClass = systemProperties.get("sun.java.command");
            if (mainClass != null && !mainClass.isEmpty()) {
                // Extract class name
                String[] parts = mainClass.split("\\s+");
                if (parts.length > 0) {
                    String className = parts[0];
                    if (className.contains(".")) {
                        className = className.substring(className.lastIndexOf('.') + 1);
                    }
                    return className;
                }
            }
            
        } catch (java.io.IOException e) {
            logger.debug("Failed to get application name: {}", e.getMessage());
        }

        return null;
    }

    /**
     * Discovers local Java processes with JMX enabled using the Attach API.
     */
    private List<JmxConnectionInfo> discoverLocalProcesses() {
        logger.info("Discovering local Java processes with JMX enabled...");
        List<JmxConnectionInfo> discovered = new ArrayList<>();

        try {
            String currentPid = getCurrentProcessId();
            logger.debug("Current MCP server PID: {}", currentPid);

            List<VirtualMachineDescriptor> vms = VirtualMachine.list();
            logger.debug("Found {} local Java processes", vms.size());

            for (VirtualMachineDescriptor vmd : vms) {
                // Skip our own process
                if (vmd.id().equals(currentPid)) {
                    logger.debug("Skipping self (PID: {})", vmd.id());
                    continue;
                }

                processVirtualMachine(vmd, discovered);
            }

        } catch (Exception e) {
            logger.warn("Local process discovery failed: {}", e.getMessage());
        }

        logger.info("Local process discovery completed. Found {} JMX-enabled processes", discovered.size());
        return discovered;
    }

    /**
     * Processes a single virtual machine for JMX discovery
     */
    private void processVirtualMachine(VirtualMachineDescriptor vmd, List<JmxConnectionInfo> discovered) {
        try {
            Optional<JmxConnectionInfo> connectionInfo = attachToProcess(vmd);
            connectionInfo.ifPresent(discovered::add);
        } catch (Exception e) {
            logger.debug("Failed to attach to process {} ({}): {}",
                vmd.id(), vmd.displayName(), e.getMessage());
        }
    }

    /**
     * Gets the current process ID
     */
    private String getCurrentProcessId() {
        try {
            String name = ManagementFactory.getRuntimeMXBean().getName();
            return name.split("@")[0];
        } catch (Exception e) {
            logger.warn("Could not determine current process ID: {}", e.getMessage());
            return "unknown";
        }
    }

    /**
     * Attempts to attach to a process and extract JMX connection information
     */
    private Optional<JmxConnectionInfo> attachToProcess(VirtualMachineDescriptor vmd) {
        VirtualMachine vm = null;
        try {
            logger.debug("Attempting to attach to process {} ({})", vmd.id(), vmd.displayName());
            vm = VirtualMachine.attach(vmd.id());

            // Get agent properties to check for JMX
            Properties agentProps = vm.getAgentProperties();
            String jmxUrl = agentProps.getProperty("com.sun.management.jmxremote.localConnectorAddress");

            if (jmxUrl == null) {
                jmxUrl = startManagementAgent(vm, vmd.id());
            }

            if (jmxUrl != null) {
                // Test the connection and get MBean count
                Optional<Integer> mbeanCount = testAndCountMBeans(jmxUrl);
                if (mbeanCount.isPresent()) {
                    String connectionId = "local-" + vmd.id();
                    String connectionName = String.format("Local Process %s (%s)", vmd.id(),
                        getProcessDisplayName(vmd.displayName()));

                    JmxConnectionInfo connectionInfo = JmxConnectionInfo.createRemote(
                        connectionId, connectionName, jmxUrl, null, null, Map.of());

                    logger.info("Discovered local JMX process: {} -> {} ({} MBeans)",
                        connectionName, jmxUrl, mbeanCount.get());
                    return Optional.of(connectionInfo.withStatus(
                        JmxConnectionInfo.ConnectionStatus.DISCONNECTED, null));
                } else {
                    logger.debug("JMX URL found but connection test failed for process {}", vmd.id());
                }
            } else {
                logger.debug("No JMX URL available for process {} ({})", vmd.id(), vmd.displayName());
            }

        } catch (AttachNotSupportedException e) {
            logger.debug("Attach not supported for process {} ({}): {}", vmd.id(), vmd.displayName(), e.getMessage());
        } catch (java.io.IOException | RuntimeException e) {
            logger.debug("Failed to attach to process {} ({}): {}", vmd.id(), vmd.displayName(), e.getMessage());
        } finally {
            if (vm != null) {
                try {
                    vm.detach();
                } catch (java.io.IOException | RuntimeException e) {
                    logger.debug("Error detaching from process {}: {}", vmd.id(), e.getMessage());
                }
            }
        }

        return Optional.empty();
    }

    /**
     * Starts the management agent for a process
     */
    private String startManagementAgent(VirtualMachine vm, String processId) {
        try {
            logger.debug("JMX not enabled for process {}, attempting to start management agent", processId);
            vm.startLocalManagementAgent();
            Properties agentProps = vm.getAgentProperties();
            return agentProps.getProperty("com.sun.management.jmxremote.localConnectorAddress");
        } catch (java.io.IOException | RuntimeException e) {
            logger.debug("Could not start management agent for process {}: {}", processId, e.getMessage());
            return null;
        }
    }

    /**
     * Tests a local JMX connection and returns the MBean count
     */
    private Optional<Integer> testAndCountMBeans(String jmxUrl) {
        try {
            JMXServiceURL serviceURL = new JMXServiceURL(jmxUrl);
            try (JMXConnector connector = JMXConnectorFactory.connect(serviceURL, null)) {
                MBeanServerConnection connection = connector.getMBeanServerConnection();
                int mbeanCount = connection.getMBeanCount();
                logger.debug("Successfully connected to {} with {} MBeans", jmxUrl, mbeanCount);
                return Optional.of(mbeanCount);
            }
        } catch (java.io.IOException | RuntimeException e) {
            logger.debug("Local JMX connection test failed for {}: {}", jmxUrl, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Cleans up the process display name for better readability
     */
    private String getProcessDisplayName(String displayName) {
        if (displayName == null || displayName.isEmpty()) {
            return "Unknown";
        }

        // Extract main class name from command line
        String[] parts = displayName.split("\\s+");
        if (parts.length > 0) {
            String mainClass = parts[0];
            if (mainClass.contains(".")) {
                mainClass = mainClass.substring(mainClass.lastIndexOf('.') + 1);
            }
            return mainClass;
        }

        return displayName;
    }
    
    /**
     * Auto-registers discovered connections with the registry
     */
    public int autoRegisterDiscoveredConnections() {
        DiscoveryResult result = discoverJmxServices();
        int registered = 0;
        
        for (JmxConnectionInfo connectionInfo : result.discoveredConnections()) {
            if (!connectionRegistry.hasConnection(connectionInfo.id())) {
                connectionRegistry.addConnection(connectionInfo);
                registered++;
                logger.info("Auto-registered JMX connection: {}", connectionInfo.name());
            } else {
                logger.debug("Connection already exists: {}", connectionInfo.id());
            }
        }
        
        logger.info("Auto-registered {} new JMX connections", registered);
        return registered;
    }
    
    /**
     * Result of JMX discovery operation
     */
    public record DiscoveryResult(
        List<JmxConnectionInfo> discoveredConnections,
        List<String> errors
    ) {
        /**
         * Creates a new DiscoveryResult with defensive copies of the collections
         */
        public DiscoveryResult(List<JmxConnectionInfo> discoveredConnections, List<String> errors) {
            this.discoveredConnections = List.copyOf(discoveredConnections != null ? discoveredConnections : List.of());
            this.errors = List.copyOf(errors != null ? errors : List.of());
        }

        public boolean hasErrors() {
            return !errors.isEmpty();
        }

        public int getDiscoveredCount() {
            return discoveredConnections.size();
        }
    }
    
    /**
     * Cleanup resources
     */
    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
