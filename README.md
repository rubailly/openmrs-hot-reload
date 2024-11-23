# OpenMRS Hot Reload Tool 🔄

A development tool that enables hot reloading of OpenMRS module classes without server restarts.

## Overview 🚀

The OpenMRS Hot Reload Tool provides a seamless development experience by:

- Watching your module's compiled classes for changes
- Automatically reloading modified classes through WebSocket connections
- Enabling rapid development without constant server restarts
- Providing real-time status updates on reload operations

## How It Works ⚙️

The tool consists of two main components:

1. **Spring Boot Application**: Runs on your development machine to watch for class changes and manage WebSocket connections

2. **OpenMRS Module**: Handles the server-side class reloading through the ModuleClassLoader

The process flow is:
1. File watcher detects changes in compiled .class files
2. Changed classes are sent via WebSocket to OpenMRS
3. ModuleClassLoader loads the new class definitions
4. Module is restarted to apply changes

## Quick Start 🏃

### Prerequisites

- Java 8+
- Maven 3.6+
- Running OpenMRS instance with the Hot Reload module installed

### Setup

1. Build and start the Spring Boot application:
```bash
git clone https://github.com/openmrs/openmrs-hot-reload.git
cd openmrs-hot-reload
mvn clean install
java -jar target/openmrs-hot-reload-1.0.0-SNAPSHOT.jar
```

2. Configure the module directory in application.yml:
```yaml
hotreload:
  moduleDir: /path/to/your/module/target/classes
```

The tool will automatically:
- Watch the configured directory for class changes
- Establish WebSocket connections to OpenMRS
- Handle class reloading when changes are detected

## Configuration ⚙️

application.yml options:
```yaml
server:
  port: 8081

hotreload:
  moduleDir: ${MODULE_DIR:#{null}}  # Path to module's target/classes directory
```

## Development Workflow 💻

1. Install the Hot Reload module in OpenMRS
2. Start the Spring Boot application
3. Make changes to your module code
4. Compile the changes (IDE or Maven)
5. Changes are automatically detected and reloaded

The tool provides real-time status updates through WebSocket messages at:
```
ws://localhost:8081/topic/status
```

## Contributing 🤝

We welcome contributions! Please:

1. Fork the repository
2. Create a feature branch
3. Submit a pull request

## License 📄

Mozilla Public License 2.0 with Healthcare Disclaimer

## Support 🆘

- [OpenMRS Talk](https://talk.openmrs.org)
- [OpenMRS Wiki](https://wiki.openmrs.org)
- [OpenMRS JIRA](https://issues.openmrs.org)
