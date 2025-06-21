package org.jmxmcp.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import javax.management.ObjectName;
import java.time.Instant;
import java.util.Map;

/**
 * Represents the result of a JMX operation execution.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record JmxOperationResult(
    ObjectName objectName,
    String operationName,
    Object result,
    String resultType,
    boolean success,
    String errorMessage,
    Instant timestamp,
    long executionTimeMs
) {

    /**
     * Creates a successful operation result.
     */
    public static JmxOperationResult success(
            ObjectName objectName,
            String operationName,
            Object result,
            long executionTimeMs) {
        
        String resultType = result != null ? result.getClass().getSimpleName() : "void";
        
        return new JmxOperationResult(
            objectName,
            operationName,
            result,
            resultType,
            true,
            null,
            Instant.now(),
            executionTimeMs
        );
    }

    /**
     * Creates a failed operation result.
     */
    public static JmxOperationResult failure(
            ObjectName objectName,
            String operationName,
            String errorMessage,
            long executionTimeMs) {
        
        return new JmxOperationResult(
            objectName,
            operationName,
            null,
            null,
            false,
            errorMessage,
            Instant.now(),
            executionTimeMs
        );
    }

    /**
     * Converts the result to a map for JSON serialization.
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = Map.of(
            "objectName", objectName.toString(),
            "operationName", operationName,
            "success", success,
            "timestamp", timestamp.toString(),
            "executionTimeMs", executionTimeMs
        );

        if (success && result != null) {
            map = new java.util.HashMap<>(map);
            map.put("result", result);
            map.put("resultType", resultType);
        }

        if (!success && errorMessage != null) {
            map = new java.util.HashMap<>(map);
            map.put("errorMessage", errorMessage);
        }

        return map;
    }

    /**
     * Gets a formatted string representation of the result.
     */
    public String getFormattedResult() {
        if (!success) {
            return String.format("ERROR: %s", errorMessage);
        }

        if (result == null) {
            return "Operation completed successfully (void return)";
        }

        return String.format("Result (%s): %s", resultType, result);
    }
}
