# Contributing to JMX MCP Server

Thank you for your interest in contributing to the JMX MCP Server! This document provides guidelines and information for contributors.

## 🚀 Getting Started

### Prerequisites

- Java 17 or higher
- Maven 3.6 or higher
- Git
- IDE of your choice (IntelliJ IDEA, Eclipse, VS Code)

### Development Setup

1. **Fork the repository**
   ```bash
   # Click the "Fork" button on GitHub, then clone your fork
   git clone https://github.com/your-username/jmx-mcp-server.git
   cd jmx-mcp-server
   ```

2. **Set up upstream remote**
   ```bash
   git remote add upstream https://github.com/original-owner/jmx-mcp-server.git
   ```

3. **Build the project**
   ```bash
   mvn clean compile
   ```

4. **Run tests**
   ```bash
   mvn test
   ```

5. **Run the comprehensive test**
   ```bash
   python3 comprehensive-test.py
   ```

## 🔧 Development Guidelines

### Code Style

- **Java Conventions**: Follow standard Java naming conventions
- **Formatting**: Use consistent indentation (4 spaces)
- **Line Length**: Keep lines under 120 characters
- **Comments**: Write clear, concise JavaDoc for public methods
- **Imports**: Organize imports and remove unused ones

### Commit Messages

Use conventional commit format:

```
type(scope): description

[optional body]

[optional footer]
```

**Types:**
- `feat`: New feature
- `fix`: Bug fix
- `docs`: Documentation changes
- `style`: Code style changes
- `refactor`: Code refactoring
- `test`: Adding or updating tests
- `chore`: Maintenance tasks

**Examples:**
```
feat(jmx): add support for remote JMX connections
fix(security): validate ObjectName parameters
docs(readme): update installation instructions
test(integration): add comprehensive MCP protocol tests
```

### Branch Naming

- `feature/description` - New features
- `fix/description` - Bug fixes
- `docs/description` - Documentation updates
- `refactor/description` - Code refactoring

## 🧪 Testing

### Unit Tests

- Write unit tests for all new functionality
- Maintain test coverage above 80%
- Use descriptive test method names
- Follow the AAA pattern (Arrange, Act, Assert)

```java
@Test
void shouldReturnMemoryUsageWhenAttributeExists() {
    // Arrange
    String objectName = "java.lang:type=Memory";
    String attributeName = "HeapMemoryUsage";
    
    // Act
    String result = jmxService.getAttribute(objectName, attributeName);
    
    // Assert
    assertThat(result).contains("HeapMemoryUsage");
}
```

### Integration Tests

- Test MCP protocol compliance
- Verify tool registration and execution
- Test resource discovery and access
- Validate error handling scenarios

### Running Tests

```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=JmxServiceTest

# Run with coverage
mvn test jacoco:report

# Run comprehensive integration test
python3 comprehensive-test.py
```

## 📝 Documentation

### JavaDoc

Write comprehensive JavaDoc for:
- All public classes and interfaces
- All public methods
- Complex private methods
- Configuration properties

```java
/**
 * Retrieves the value of a specific MBean attribute.
 * 
 * @param objectName the ObjectName of the MBean
 * @param attributeName the name of the attribute to retrieve
 * @return the attribute value as a formatted string
 * @throws IllegalArgumentException if objectName is invalid
 * @throws SecurityException if access is denied
 */
public String getAttribute(String objectName, String attributeName) {
    // Implementation
}
```

### README Updates

- Update feature lists when adding new capabilities
- Add examples for new tools or resources
- Update configuration documentation
- Keep troubleshooting section current

## 🐛 Bug Reports

### Before Submitting

1. Check existing issues for duplicates
2. Test with the latest version
3. Reproduce the issue consistently
4. Gather relevant information

### Bug Report Template

```markdown
**Describe the bug**
A clear description of what the bug is.

**To Reproduce**
Steps to reproduce the behavior:
1. Configure server with '...'
2. Execute tool '...'
3. See error

**Expected behavior**
What you expected to happen.

**Environment:**
- OS: [e.g., macOS 12.0]
- Java version: [e.g., OpenJDK 17.0.1]
- Server version: [e.g., 1.0.0]
- Claude Desktop version: [e.g., 0.7.1]

**Logs**
Relevant log output or error messages.

**Additional context**
Any other context about the problem.
```

## 💡 Feature Requests

### Before Submitting

1. Check if the feature already exists
2. Review existing feature requests
3. Consider if it fits the project scope
4. Think about implementation complexity

### Feature Request Template

```markdown
**Is your feature request related to a problem?**
A clear description of what the problem is.

**Describe the solution you'd like**
A clear description of what you want to happen.

**Describe alternatives you've considered**
Other solutions or features you've considered.

**Additional context**
Any other context, mockups, or examples.
```

## 🔄 Pull Request Process

### Before Submitting

1. **Update your fork**
   ```bash
   git fetch upstream
   git checkout main
   git merge upstream/main
   ```

2. **Create a feature branch**
   ```bash
   git checkout -b feature/your-feature-name
   ```

3. **Make your changes**
   - Write code following the style guidelines
   - Add tests for new functionality
   - Update documentation as needed

4. **Test your changes**
   ```bash
   mvn clean test
   python3 comprehensive-test.py
   ```

5. **Commit your changes**
   ```bash
   git add .
   git commit -m "feat: add your feature description"
   ```

6. **Push to your fork**
   ```bash
   git push origin feature/your-feature-name
   ```

### Pull Request Template

```markdown
**Description**
Brief description of changes made.

**Type of change**
- [ ] Bug fix (non-breaking change which fixes an issue)
- [ ] New feature (non-breaking change which adds functionality)
- [ ] Breaking change (fix or feature that would cause existing functionality to not work as expected)
- [ ] Documentation update

**Testing**
- [ ] Unit tests pass
- [ ] Integration tests pass
- [ ] Comprehensive test passes
- [ ] Manual testing completed

**Checklist**
- [ ] Code follows style guidelines
- [ ] Self-review completed
- [ ] Code is commented, particularly in hard-to-understand areas
- [ ] Documentation updated
- [ ] No new warnings introduced
```

### Review Process

1. **Automated Checks**: CI/CD pipeline runs tests
2. **Code Review**: Maintainers review the code
3. **Feedback**: Address any requested changes
4. **Approval**: Once approved, the PR will be merged

## 🏷️ Release Process

### Versioning

We use [Semantic Versioning](https://semver.org/):
- **MAJOR**: Incompatible API changes
- **MINOR**: New functionality (backward compatible)
- **PATCH**: Bug fixes (backward compatible)

### Release Checklist

1. Update version in `pom.xml`
2. Update `CHANGELOG.md`
3. Create release notes
4. Tag the release
5. Build and test release artifacts
6. Publish to Maven Central (if applicable)
7. Update documentation

## 🤝 Community

### Code of Conduct

- Be respectful and inclusive
- Welcome newcomers and help them learn
- Focus on constructive feedback
- Respect different viewpoints and experiences

### Communication

- **GitHub Issues**: Bug reports and feature requests
- **GitHub Discussions**: General questions and community discussions
- **Pull Requests**: Code contributions and reviews

## 📚 Resources

- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
- [Spring AI MCP Documentation](https://docs.spring.io/spring-ai/reference/api/mcp.html)
- [Model Context Protocol Specification](https://modelcontextprotocol.io/)
- [JMX Technology](https://docs.oracle.com/javase/tutorial/jmx/)

## 🙏 Recognition

Contributors will be recognized in:
- `CONTRIBUTORS.md` file
- Release notes
- Project documentation

Thank you for contributing to the JMX MCP Server! 🎉
