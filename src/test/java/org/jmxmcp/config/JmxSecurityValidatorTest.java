package org.jmxmcp.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.management.ObjectName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for JmxSecurityValidator.
 */
class JmxSecurityValidatorTest {

    private JmxSecurityValidator validator;
    private JmxConnectionProperties properties;

    @BeforeEach
    void setUp() {
        // Use default properties for testing
        properties = new JmxConnectionProperties();
        validator = new JmxSecurityValidator(properties);
    }

    @Test
    void testValidObjectName() throws Exception {
        ObjectName objectName = new ObjectName("java.lang:type=Memory");
        JmxSecurityValidator.ValidationResult result = validator.validateObjectName(objectName);
        assertTrue(result.isValid());
    }

    @Test
    void testNullObjectName() {
        JmxSecurityValidator.ValidationResult result = validator.validateObjectName(null);
        assertFalse(result.isValid());
        assertEquals("ObjectName cannot be null", result.getErrorMessage());
    }

    @Test
    void testValidOperationName() {
        JmxSecurityValidator.ValidationResult result = validator.validateOperationName("getHeapMemoryUsage");
        assertTrue(result.isValid());
    }

    @Test
    void testNullOperationName() {
        JmxSecurityValidator.ValidationResult result = validator.validateOperationName(null);
        assertFalse(result.isValid());
        assertEquals("Operation name cannot be null or empty", result.getErrorMessage());
    }

    @Test
    void testEmptyOperationName() {
        JmxSecurityValidator.ValidationResult result = validator.validateOperationName("");
        assertFalse(result.isValid());
        assertEquals("Operation name cannot be null or empty", result.getErrorMessage());
    }

    @Test
    void testValidJsonArguments() {
        String validJson = "{\"param1\": \"value1\", \"param2\": 123}";
        JmxSecurityValidator.ValidationResult result = validator.validateArguments(validJson);
        assertTrue(result.isValid());
    }

    @Test
    void testInvalidJsonArguments() {
        String invalidJson = "{invalid json}";
        JmxSecurityValidator.ValidationResult result = validator.validateArguments(invalidJson);
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("Invalid JSON arguments"));
    }

    @Test
    void testMaliciousArguments() {
        String maliciousJson = "{\"param\": \"<script>alert('xss')</script>\"}";
        JmxSecurityValidator.ValidationResult result = validator.validateArguments(maliciousJson);
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("malicious content"));
    }

    @Test
    void testSanitizeValue() {
        String maliciousValue = "<script>alert('test')</script>";
        String sanitized = validator.sanitizeValue(maliciousValue);
        assertFalse(sanitized.contains("<script>"));
        assertFalse(sanitized.contains("</script>"));
    }

    @Test
    void testValidationResultCreation() {
        JmxSecurityValidator.ValidationResult valid = JmxSecurityValidator.ValidationResult.valid();
        assertTrue(valid.isValid());
        assertNull(valid.getErrorMessage());

        JmxSecurityValidator.ValidationResult invalid = JmxSecurityValidator.ValidationResult.invalid("Test error");
        assertFalse(invalid.isValid());
        assertEquals("Test error", invalid.getErrorMessage());
    }
}
