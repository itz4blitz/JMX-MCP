package org.jmxmcp;


import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main application class for the JMX MCP Server.
 * 
 * This application provides a Model Context Protocol (MCP) server that bridges
 * Java Management Extensions (JMX) with AI models, allowing AI to monitor and
 * manage Java applications through standardized MCP interfaces.
 * 
 * Features:
 * - Exposes JMX operations as MCP tools
 * - Exposes JMX attributes as MCP resources
 * - Supports both local and remote JMX connections
 * - Dynamic MBean discovery and registration
 * - Configurable security and access controls
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class JmxMcpServerApplication {



    public static void main(String[] args) {
        // Note: No logging in main() for STDIO mode - MCP protocol requires clean JSON output
        SpringApplication.run(JmxMcpServerApplication.class, args);
    }
}
