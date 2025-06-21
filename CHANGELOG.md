# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Initial open source release preparation
- GitHub Actions CI/CD workflows
- Comprehensive documentation
- Security policy and contributing guidelines

## [1.0.0] - 2025-06-21

### Added
- **Core Features**
  - Complete MCP (Model Context Protocol) server implementation
  - JMX connection management with auto-discovery
  - Comprehensive MBean exploration and management
  - Real-time monitoring capabilities
  - Security validation and access control

- **Connection Types**
  - Local JMX connections via Attach API
  - Remote JMX connections with authentication
  - Multi-connection support with connection registry
  - Automatic JMX service discovery

- **MCP Tools**
  - `discover_jmx_services` - Auto-discover JMX-enabled applications
  - `list_mbeans` - Browse available MBeans
  - `get_mbean_info` - Detailed MBean information
  - `get_attribute` - Read MBean attributes
  - `set_attribute` - Modify MBean attributes
  - `invoke_operation` - Execute MBean operations
  - `list_connections` - Manage multiple JMX connections
  - `add_connection` - Add new JMX connections
  - `switch_connection` - Switch between connections

- **MCP Resources**
  - `jmx://connections` - Connection registry overview
  - `jmx://mbeans/{domain}` - Domain-specific MBean listings
  - `jmx://mbean/{objectName}` - Individual MBean details

- **Configuration Profiles**
  - STDIO mode for Claude Desktop integration
  - Multi-connection mode for managing multiple applications
  - Remote monitoring mode for production environments
  - Docker deployment configuration

- **Security Features**
  - Input validation and sanitization
  - Configurable operation filtering
  - Security policy enforcement
  - Audit logging

- **Documentation**
  - Comprehensive README with setup instructions
  - Configuration examples and use cases
  - Troubleshooting guide
  - API documentation

### Technical Details
- **Framework**: Spring Boot 3.4.1
- **Java**: Requires Java 17+
- **MCP Protocol**: Version 2024-11-05
- **Package**: `org.jmxmcp.*` (open source ready)
- **Build**: Maven with comprehensive test suite
- **Testing**: 36 unit and integration tests

### Supported Platforms
- **Operating Systems**: Windows, macOS, Linux
- **Java Applications**: Any JMX-enabled Java application
- **AI Assistants**: Claude Desktop with MCP support

[1.0.0]: https://github.com/itz4blitz/jmx-mcp-server/releases/tag/v1.0.0
[Unreleased]: https://github.com/itz4blitz/jmx-mcp-server/compare/v1.0.0...HEAD
