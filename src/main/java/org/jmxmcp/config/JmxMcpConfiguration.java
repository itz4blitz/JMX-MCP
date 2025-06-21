package org.jmxmcp.config;

import org.jmxmcp.jmx.JMXConnectionManager;
import org.jmxmcp.jmx.JMXToMCPMapper;
import org.jmxmcp.jmx.JmxConnectionRegistry;
import org.jmxmcp.service.JmxDiscoveryService;
import org.jmxmcp.service.JmxService;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;



/**
 * Main configuration class for the JMX MCP Server.
 * 
 * This configuration sets up all the necessary beans for the JMX MCP server
 * including connection management, discovery services, and MCP providers.
 */
@Configuration
public class JmxMcpConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(JmxMcpConfiguration.class);

    /**
     * Creates the JMX connection properties bean with explicit name for SpEL expressions.
     */
    @Bean("jmxConnectionProperties")
    @ConfigurationProperties(prefix = "jmx.mcp")
    public JmxConnectionProperties jmxConnectionProperties() {
        return new JmxConnectionProperties();
    }

    /**
     * Creates the JMX connection registry bean.
     */
    @Bean
    public JmxConnectionRegistry jmxConnectionRegistry() {
        logger.info("Creating JMX connection registry");
        return new JmxConnectionRegistry();
    }

    /**
     * Creates the JMX connection manager bean with connection registry support.
     */
    @Bean
    public JMXConnectionManager jmxConnectionManager(JmxConnectionProperties properties,
                                                    JmxConnectionRegistry connectionRegistry) {
        logger.info("Creating JMX connection manager with type: {}", properties.connection().type());
        return new JMXConnectionManager(properties, connectionRegistry);
    }

    /**
     * Creates the JMX discovery service bean.
     */
    @Bean
    public JmxDiscoveryService jmxDiscoveryService(JmxConnectionRegistry connectionRegistry) {
        logger.info("Creating JMX discovery service");
        return new JmxDiscoveryService(connectionRegistry);
    }

    // MBeanDiscoveryService is automatically created via @Service annotation

    /**
     * Creates the JMX to MCP mapper bean.
     */
    @Bean
    public JMXToMCPMapper jmxToMcpMapper(JmxConnectionProperties properties) {
        logger.info("Creating JMX to MCP mapper");
        return new JMXToMCPMapper(properties);
    }

    /**
     * Creates the JMX security validator bean.
     */
    @Bean
    public JmxSecurityValidator jmxSecurityValidator(JmxConnectionProperties properties) {
        logger.info("Creating JMX security validator");
        return new JmxSecurityValidator(properties);
    }

    /**
     * Explicit tool registration for JMX operations.
     * This ensures tools are properly registered in STDIO mode.
     */
    @Bean
    public ToolCallbackProvider jmxToolCallbackProvider(JmxService jmxService) {
        logger.info("Creating JMX tool callback provider for MCP server");
        return MethodToolCallbackProvider.builder()
            .toolObjects(jmxService)
            .build();
    }
}
