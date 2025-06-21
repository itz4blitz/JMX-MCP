# Security Policy

## Supported Versions

We actively support the following versions of JMX MCP Server with security updates:

| Version | Supported          |
| ------- | ------------------ |
| 1.0.x   | :white_check_mark: |

## Reporting a Vulnerability

We take security vulnerabilities seriously. If you discover a security vulnerability in JMX MCP Server, please report it responsibly.

### How to Report

1. **Do NOT** create a public GitHub issue for security vulnerabilities
2. Email security reports to: [itzdarkblitz@protonmail.com](mailto:itzdarkblitz@protonmail.com)
3. Include the following information:
   - Description of the vulnerability
   - Steps to reproduce the issue
   - Potential impact assessment
   - Any suggested fixes (if available)

### What to Expect

- **Acknowledgment**: We will acknowledge receipt of your report within 48 hours
- **Initial Assessment**: We will provide an initial assessment within 5 business days
- **Updates**: We will keep you informed of our progress throughout the investigation
- **Resolution**: We aim to resolve critical vulnerabilities within 30 days

### Security Best Practices

When using JMX MCP Server:

1. **Network Security**: Use secure connections for remote JMX access
2. **Access Control**: Implement proper authentication and authorization
3. **Monitoring**: Monitor JMX access logs for suspicious activity
4. **Updates**: Keep the server updated to the latest version
5. **Configuration**: Review security configuration settings regularly

### Scope

This security policy covers:
- JMX MCP Server core functionality
- MCP protocol implementation
- JMX connection handling
- Security validation components

### Out of Scope

- Third-party dependencies (report to respective maintainers)
- Issues in Java Virtual Machine or operating system
- Misconfigurations in deployment environments

## Security Features

JMX MCP Server includes several built-in security features:

- **Input Validation**: All inputs are validated before processing
- **Access Control**: Configurable security policies for JMX operations
- **Operation Filtering**: Dangerous operations can be blocked
- **Audit Logging**: Security events are logged for monitoring

Thank you for helping keep JMX MCP Server secure!
