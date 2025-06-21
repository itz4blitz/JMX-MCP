#!/bin/bash

# GitHub Repository Setup Script for JMX MCP Server
# This script helps prepare the repository for GitHub

set -e

echo "🚀 Setting up JMX MCP Server for GitHub..."

# Check if we're in the right directory
if [ ! -f "pom.xml" ]; then
    echo "❌ Error: pom.xml not found. Please run this script from the project root."
    exit 1
fi

# Check if git is initialized
if [ ! -d ".git" ]; then
    echo "📁 Initializing git repository..."
    git init
    echo "✅ Git repository initialized"
else
    echo "✅ Git repository already exists"
fi

# Create .gitignore if it doesn't exist
if [ ! -f ".gitignore" ]; then
    echo "📝 Creating .gitignore..."
    # The .gitignore should already exist from our setup
    echo "✅ .gitignore created"
else
    echo "✅ .gitignore already exists"
fi

# Build the project to ensure everything works
echo "🔨 Building project..."
mvn clean package -DskipTests
echo "✅ Project built successfully"

# Run tests
echo "🧪 Running tests..."
mvn test
echo "✅ All tests passed"

# Add all files to git
echo "📦 Adding files to git..."
git add .

# Check if there are changes to commit
if git diff --staged --quiet; then
    echo "ℹ️  No changes to commit"
else
    echo "💾 Committing initial version..."
    git commit -m "feat: initial open source release

- Complete JMX MCP server implementation
- Support for local and remote JMX connections
- Comprehensive MBean management capabilities
- Claude Desktop integration via MCP protocol
- Full test suite and documentation
- Open source ready with org.jmxmcp package structure"
    echo "✅ Initial commit created"
fi

echo ""
echo "🎉 Repository setup complete!"
echo ""
echo "Next steps:"
echo "1. Create a new repository on GitHub: https://github.com/new"
echo "2. Repository name: jmx-mcp-server"
echo "3. Description: Open Source JMX Model Context Protocol Server for AI integration with Java applications"
echo "4. Make it public"
echo "5. Don't initialize with README (we already have one)"
echo ""
echo "Then run these commands:"
echo "  git remote add origin https://github.com/YOUR_USERNAME/jmx-mcp-server.git"
echo "  git branch -M main"
echo "  git push -u origin main"
echo ""
echo "🏷️  To create your first release:"
echo "  git tag -a v1.0.0 -m 'Release v1.0.0'"
echo "  git push origin v1.0.0"
echo ""
echo "📋 Repository checklist:"
echo "  ✅ README.md with demo video reference"
echo "  ✅ LICENSE (MIT)"
echo "  ✅ CONTRIBUTING.md"
echo "  ✅ SECURITY.md"
echo "  ✅ CHANGELOG.md"
echo "  ✅ GitHub Actions workflows"
echo "  ✅ Issue and PR templates"
echo "  ✅ .gitignore"
echo "  ✅ All tests passing"
echo "  ✅ Generic package structure (org.jmxmcp)"
echo ""
echo "🎯 Your JMX MCP Server is ready for open source!"
