# Sample AI Prompts for JMX MCP Server

This document provides example prompts you can use with AI models when the JMX MCP Server is connected.

## Memory Management

### Check Memory Usage
```
What is the current heap memory usage? Show me both used and maximum heap memory, and calculate the percentage used.
```

### Memory Analysis
```
Analyze the current memory situation. Check heap usage, non-heap usage, and identify which memory pools are consuming the most memory.
```

### Garbage Collection
```
Trigger a garbage collection and then show me the before and after memory usage to see how much memory was freed.
```

### Memory Leak Detection
```
Monitor the heap memory usage over time and tell me if there are signs of a memory leak. Check if memory usage is consistently increasing.
```

## Performance Monitoring

### Thread Analysis
```
Analyze the current thread state. How many threads are running, blocked, or waiting? Are there any potential deadlocks?
```

### CPU and Load Monitoring
```
Check the current CPU usage and system load. Is the application under high load?
```

### Garbage Collection Performance
```
Analyze the garbage collection performance. Show me GC frequency, duration, and which collectors are being used.
```

### Application Uptime
```
How long has this Java application been running? Show me the uptime and start time.
```

## Database and Connection Monitoring

### Connection Pool Status
```
Check the database connection pool status. How many connections are active, idle, and what's the maximum pool size?
```

### Database Performance
```
Monitor database-related metrics. Show me connection counts, query performance, and any connection leaks.
```

### Connection Pool Tuning
```
Analyze the connection pool configuration and usage patterns. Are we using too many or too few connections?
```

## Application-Specific Monitoring

### Cache Performance
```
Check the application cache performance. What's the hit ratio, miss ratio, and current cache size?
```

### Business Metrics
```
Show me the current business metrics exposed via JMX. This might include transaction counts, error rates, or custom application metrics.
```

### Configuration Validation
```
Check the current application configuration values exposed via JMX. Are all settings configured correctly?
```

## Troubleshooting

### Performance Issues
```
The application seems slow. Help me diagnose the issue by checking memory usage, thread states, GC activity, and any other relevant metrics.
```

### Memory Issues
```
The application is running out of memory. Analyze the current memory usage and suggest what might be causing high memory consumption.
```

### Thread Issues
```
Check for thread-related issues. Are there any deadlocks, too many threads, or threads stuck in waiting states?
```

### Resource Leaks
```
Help me identify potential resource leaks. Check for increasing memory usage, growing thread counts, or unclosed connections.
```

## Capacity Planning

### Resource Utilization
```
Analyze the current resource utilization. Based on memory, CPU, and thread usage, how much headroom do we have?
```

### Scaling Recommendations
```
Based on the current metrics, do you recommend scaling up (more resources) or scaling out (more instances)?
```

### Performance Baseline
```
Establish a performance baseline by capturing current memory usage, thread counts, GC metrics, and response times.
```

## Maintenance Operations

### Pre-Deployment Check
```
Before deploying a new version, check the current application health. Capture baseline metrics for comparison.
```

### Post-Deployment Validation
```
After deployment, validate that the application is healthy. Compare current metrics with the pre-deployment baseline.
```

### Scheduled Maintenance
```
Perform routine health checks. Verify memory usage is stable, no thread leaks, and GC is performing well.
```

## Advanced Analysis

### Memory Pool Analysis
```
Analyze each memory pool separately. Show me Eden space, survivor spaces, old generation, and metaspace usage.
```

### GC Algorithm Analysis
```
Analyze the garbage collection algorithm being used. Is it appropriate for this application's workload?
```

### JVM Tuning Recommendations
```
Based on the current metrics, do you have any JVM tuning recommendations? Should we adjust heap sizes or GC settings?
```

### Comparative Analysis
```
Compare current metrics with historical data (if available) or typical values for similar applications.
```

## Monitoring Automation

### Health Check
```
Perform a comprehensive health check. Verify all critical metrics are within normal ranges and alert me to any issues.
```

### Anomaly Detection
```
Look for any anomalies in the current metrics. Are there any values that seem unusual or concerning?
```

### Trend Analysis
```
If you have access to historical data, analyze trends in memory usage, thread counts, and GC frequency.
```

## Emergency Response

### Out of Memory Investigation
```
The application is experiencing OutOfMemoryErrors. Help me investigate by checking heap usage, memory pools, and potential causes.
```

### High CPU Investigation
```
CPU usage is very high. Help me identify the cause by checking thread states, GC activity, and any blocking operations.
```

### Application Hang Investigation
```
The application appears to be hanging. Check for deadlocks, blocked threads, and any other issues that might cause unresponsiveness.
```

## Custom Application Metrics

### Business KPIs
```
Show me the current business KPIs exposed via JMX. This might include order processing rates, user session counts, or revenue metrics.
```

### Error Rates
```
Check the current error rates and exception counts. Are there any concerning trends?
```

### Feature Usage
```
If the application exposes feature usage metrics via JMX, show me which features are being used most frequently.
```

## Tips for Effective Prompts

1. **Be Specific**: Instead of "check memory", ask for "heap memory usage percentage and identify the largest memory pools"

2. **Ask for Analysis**: Don't just ask for raw numbers, ask the AI to interpret and analyze the data

3. **Request Comparisons**: Ask for comparisons with normal ranges or previous measurements

4. **Combine Metrics**: Ask for multiple related metrics together for better context

5. **Ask for Recommendations**: Request actionable recommendations based on the metrics

6. **Use Context**: Provide context about what you're investigating or what symptoms you're seeing

Remember that the AI can only access the MBeans and attributes that are exposed by your application and included in the JMX MCP Server configuration.
