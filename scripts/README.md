# Scripts

This directory contains utility scripts for the JMX MCP Server project.

## setup-github.sh

Prepares the repository for GitHub open source release.

### Usage

```bash
./scripts/setup-github.sh
```

### What it does

1. **Validates environment** - Checks for required files and tools
2. **Builds project** - Ensures everything compiles correctly
3. **Runs tests** - Validates all functionality works
4. **Prepares git** - Initializes repository and creates initial commit
5. **Provides guidance** - Shows next steps for GitHub setup

### Prerequisites

- Git installed and configured
- Java 17+ installed
- Maven 3.6+ installed
- Project built successfully

### Output

The script will:
- ✅ Validate the project structure
- ✅ Build and test the project
- ✅ Create initial git commit
- ✅ Provide step-by-step GitHub setup instructions

After running this script, you'll be ready to push to GitHub and create your first release!
