package org.jmxmcp.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.management.ObjectName;
import java.util.List;
import java.util.Set;

/**
 * Security validator for JMX operations and parameters.
 * 
 * This validator:
 * - Validates operation parameters
 * - Checks for dangerous operations
 * - Sanitizes input values
 * - Prevents malicious ObjectName patterns
 */
@Component
public class JmxSecurityValidator {

    private static final Logger logger = LoggerFactory.getLogger(JmxSecurityValidator.class);

    private final JmxConnectionProperties properties;
    private final ObjectMapper objectMapper;

    // Dangerous ObjectName patterns that should be restricted
    private static final Set<String> DANGEROUS_DOMAINS = Set.of(
        "java.lang",
        "java.util.logging",
        "com.sun.management"
    );

    // Dangerous operation patterns
    private static final Set<String> DANGEROUS_OPERATION_PATTERNS = Set.of(
        "shutdown", "stop", "destroy", "kill", "exit", "halt",
        "restart", "reboot", "reset", "clear", "delete", "remove",
        "gc", "forceGC", "runFinalization", "dumpHeap"
    );

    public JmxSecurityValidator(JmxConnectionProperties properties) {
        this.properties = properties;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Validates an operation before execution.
     */
    public ValidationResult validateOperation(ObjectName objectName, String operationName, String arguments) {
        try {
            // Validate ObjectName
            ValidationResult objectNameResult = validateObjectName(objectName);
            if (!objectNameResult.isValid()) {
                return objectNameResult;
            }

            // Validate operation name
            ValidationResult operationResult = validateOperationName(operationName);
            if (!operationResult.isValid()) {
                return operationResult;
            }

            // Validate arguments if parameter validation is enabled
            if (properties.security().validateParameters()) {
                ValidationResult argumentsResult = validateArguments(arguments);
                if (!argumentsResult.isValid()) {
                    return argumentsResult;
                }
            }

            return ValidationResult.valid();

        } catch (Exception e) {
            logger.error("Error during security validation", e);
            return ValidationResult.invalid("Security validation failed: " + e.getMessage());
        }
    }

    /**
     * Validates an ObjectName for security concerns.
     */
    public ValidationResult validateObjectName(ObjectName objectName) {
        if (objectName == null) {
            return ValidationResult.invalid("ObjectName cannot be null");
        }

        String domain = objectName.getDomain();
        
        // Check for dangerous domains
        if (DANGEROUS_DOMAINS.contains(domain)) {
            logger.warn("Access to potentially dangerous domain: {}", domain);
            // Don't block, but log the access
        }

        // Check for malicious patterns
        String objectNameStr = objectName.toString();
        if (containsMaliciousPattern(objectNameStr)) {
            return ValidationResult.invalid("ObjectName contains potentially malicious patterns");
        }

        return ValidationResult.valid();
    }

    /**
     * Validates an operation name for security concerns.
     */
    public ValidationResult validateOperationName(String operationName) {
        if (operationName == null || operationName.isBlank()) {
            return ValidationResult.invalid("Operation name cannot be null or empty");
        }

        // Check for dangerous operations
        String lowerOperationName = operationName.toLowerCase();
        for (String dangerousPattern : DANGEROUS_OPERATION_PATTERNS) {
            if (lowerOperationName.contains(dangerousPattern)) {
                if (properties.security().dangerousOperations().contains(lowerOperationName)) {
                    logger.warn("Executing dangerous operation: {}", operationName);
                    // Allow but log the dangerous operation
                    break;
                }
            }
        }

        // Check for malicious patterns
        if (containsMaliciousPattern(operationName)) {
            return ValidationResult.invalid("Operation name contains potentially malicious patterns");
        }

        return ValidationResult.valid();
    }

    /**
     * Validates operation arguments.
     */
    public ValidationResult validateArguments(String arguments) {
        if (arguments == null || arguments.isBlank()) {
            return ValidationResult.valid(); // Empty arguments are valid
        }

        try {
            // Parse JSON to validate structure
            JsonNode argsNode = objectMapper.readTree(arguments);
            
            // Validate argument values
            return validateJsonNode(argsNode, "");
            
        } catch (JsonProcessingException | RuntimeException e) {
            return ValidationResult.invalid("Invalid JSON arguments: " + e.getMessage());
        }
    }

    /**
     * Recursively validates JSON node values.
     */
    private ValidationResult validateJsonNode(JsonNode node, String path) {
        if (node.isTextual()) {
            String value = node.asText();
            if (containsMaliciousPattern(value)) {
                return ValidationResult.invalid("Argument at path '" + path + "' contains potentially malicious content");
            }
        } else if (node.isObject()) {
            var fields = node.fields();
            while (fields.hasNext()) {
                var field = fields.next();
                String fieldPath = path.isEmpty() ? field.getKey() : path + "." + field.getKey();
                ValidationResult result = validateJsonNode(field.getValue(), fieldPath);
                if (!result.isValid()) {
                    return result;
                }
            }
        } else if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                String arrayPath = path + "[" + i + "]";
                ValidationResult result = validateJsonNode(node.get(i), arrayPath);
                if (!result.isValid()) {
                    return result;
                }
            }
        }
        
        return ValidationResult.valid();
    }

    /**
     * Checks if a string contains potentially malicious patterns.
     */
    private boolean containsMaliciousPattern(String value) {
        if (value == null) {
            return false;
        }

        String lowerValue = value.toLowerCase();
        
        // Check for script injection patterns
        List<String> maliciousPatterns = List.of(
            "<script", "javascript:", "vbscript:", "onload=", "onerror=",
            "eval(", "exec(", "system(", "runtime.exec",
            "../", "..\\", "/etc/", "c:\\", "cmd.exe", "powershell",
            "rm -rf", "del /", "format c:", "shutdown -",
            "drop table", "delete from", "insert into", "update set"
        );

        for (String pattern : maliciousPatterns) {
            if (lowerValue.contains(pattern)) {
                logger.warn("Detected potentially malicious pattern '{}' in value: {}", pattern, value);
                return true;
            }
        }

        return false;
    }

    /**
     * Sanitizes a string value by removing potentially dangerous characters.
     */
    public String sanitizeValue(String value) {
        if (value == null) {
            return null;
        }

        // Remove or escape dangerous characters
        return value
            .replaceAll("[<>\"'&]", "") // Remove HTML/XML characters
            .replaceAll("[\r\n\t]", " ") // Replace control characters with spaces
            .trim();
    }

    /**
     * Result of a security validation.
     */
    public static class ValidationResult {
        private final boolean valid;
        private final String errorMessage;

        private ValidationResult(boolean valid, String errorMessage) {
            this.valid = valid;
            this.errorMessage = errorMessage;
        }

        public static ValidationResult valid() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult invalid(String errorMessage) {
            return new ValidationResult(false, errorMessage);
        }

        public boolean isValid() {
            return valid;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }
}
