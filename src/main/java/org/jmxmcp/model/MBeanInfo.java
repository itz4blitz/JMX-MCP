package org.jmxmcp.model;

import javax.management.ObjectName;
import java.util.List;
import java.util.Map;

/**
 * Represents comprehensive information about an MBean including its
 * attributes, operations, and notifications.
 */
public record MBeanInfo(
    ObjectName objectName,
    String className,
    String description,
    List<MBeanAttributeInfo> attributes,
    List<MBeanOperationInfo> operations,
    List<MBeanNotificationInfo> notifications
) {

    /**
     * Information about an MBean attribute.
     */
    public record MBeanAttributeInfo(
        String name,
        String type,
        String description,
        boolean readable,
        boolean writable,
        boolean isIs
    ) {}

    /**
     * Information about an MBean operation.
     */
    public record MBeanOperationInfo(
        String name,
        String description,
        String returnType,
        List<MBeanParameterInfo> parameters,
        int impact
    ) {
        
        /**
         * Gets the impact as a human-readable string.
         */
        public String getImpactString() {
            return switch (impact) {
                case javax.management.MBeanOperationInfo.INFO -> "INFO";
                case javax.management.MBeanOperationInfo.ACTION -> "ACTION";
                case javax.management.MBeanOperationInfo.ACTION_INFO -> "ACTION_INFO";
                case javax.management.MBeanOperationInfo.UNKNOWN -> "UNKNOWN";
                default -> "UNKNOWN";
            };
        }
    }

    /**
     * Information about an MBean operation parameter.
     */
    public record MBeanParameterInfo(
        String name,
        String type,
        String description
    ) {}

    /**
     * Information about an MBean notification.
     */
    public record MBeanNotificationInfo(
        String name,
        String description,
        List<String> notificationTypes
    ) {}

    /**
     * Creates MBeanInfo from JMX MBeanInfo.
     */
    public static MBeanInfo from(ObjectName objectName, javax.management.MBeanInfo jmxInfo) {
        List<MBeanAttributeInfo> attributes = List.of(jmxInfo.getAttributes()).stream()
            .map(attr -> new MBeanAttributeInfo(
                attr.getName(),
                attr.getType(),
                attr.getDescription(),
                attr.isReadable(),
                attr.isWritable(),
                attr.isIs()
            ))
            .toList();

        List<MBeanOperationInfo> operations = List.of(jmxInfo.getOperations()).stream()
            .map(op -> new MBeanOperationInfo(
                op.getName(),
                op.getDescription(),
                op.getReturnType(),
                List.of(op.getSignature()).stream()
                    .map(param -> new MBeanParameterInfo(
                        param.getName(),
                        param.getType(),
                        param.getDescription()
                    ))
                    .toList(),
                op.getImpact()
            ))
            .toList();

        List<MBeanNotificationInfo> notifications = List.of(jmxInfo.getNotifications()).stream()
            .map(notif -> new MBeanNotificationInfo(
                notif.getName(),
                notif.getDescription(),
                List.of(notif.getNotifTypes())
            ))
            .toList();

        return new MBeanInfo(
            objectName,
            jmxInfo.getClassName(),
            jmxInfo.getDescription(),
            attributes,
            operations,
            notifications
        );
    }

    /**
     * Gets a specific attribute by name.
     */
    public MBeanAttributeInfo getAttribute(String name) {
        return attributes.stream()
            .filter(attr -> attr.name().equals(name))
            .findFirst()
            .orElse(null);
    }

    /**
     * Gets a specific operation by name.
     */
    public MBeanOperationInfo getOperation(String name) {
        return operations.stream()
            .filter(op -> op.name().equals(name))
            .findFirst()
            .orElse(null);
    }

    /**
     * Gets operations by name (there can be multiple with different signatures).
     */
    public List<MBeanOperationInfo> getOperations(String name) {
        return operations.stream()
            .filter(op -> op.name().equals(name))
            .toList();
    }

    /**
     * Gets readable attributes.
     */
    public List<MBeanAttributeInfo> getReadableAttributes() {
        return attributes.stream()
            .filter(MBeanAttributeInfo::readable)
            .toList();
    }

    /**
     * Gets writable attributes.
     */
    public List<MBeanAttributeInfo> getWritableAttributes() {
        return attributes.stream()
            .filter(MBeanAttributeInfo::writable)
            .toList();
    }

    /**
     * Converts to a map for JSON serialization.
     */
    public Map<String, Object> toMap() {
        return Map.of(
            "objectName", objectName.toString(),
            "className", className,
            "description", description != null ? description : "",
            "attributes", attributes.stream()
                .map(attr -> Map.of(
                    "name", attr.name(),
                    "type", attr.type(),
                    "description", attr.description() != null ? attr.description() : "",
                    "readable", attr.readable(),
                    "writable", attr.writable()
                ))
                .toList(),
            "operations", operations.stream()
                .map(op -> Map.of(
                    "name", op.name(),
                    "description", op.description() != null ? op.description() : "",
                    "returnType", op.returnType(),
                    "impact", op.getImpactString(),
                    "parameters", op.parameters().stream()
                        .map(param -> Map.of(
                            "name", param.name(),
                            "type", param.type(),
                            "description", param.description() != null ? param.description() : ""
                        ))
                        .toList()
                ))
                .toList(),
            "notifications", notifications.stream()
                .map(notif -> Map.of(
                    "name", notif.name(),
                    "description", notif.description() != null ? notif.description() : "",
                    "types", notif.notificationTypes()
                ))
                .toList()
        );
    }
}
