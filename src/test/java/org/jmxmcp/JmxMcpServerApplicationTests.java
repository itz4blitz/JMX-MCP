package org.jmxmcp;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Integration tests for the JMX MCP Server application.
 */
@SpringBootTest
@ActiveProfiles("test")
class JmxMcpServerApplicationTests {

    @Test
    void contextLoads() {
        // Test that the Spring context loads successfully
    }
}
