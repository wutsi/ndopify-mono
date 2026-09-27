# Setup Guide - ndopify-dto

## Table of Contents

- [Prerequisites](#prerequisites)
- [Installation](#installation)
- [Configuration](#configuration)
- [Running Tests](#running-tests)

## Prerequisites

The ndopify-dto is a pure data transfer object (DTO) library with no runtime dependencies. To work with it:

- **Java Development Kit (JDK) 17** or higher
    - Verify: `java -version`
    - Download: [Oracle JDK](https://www.oracle.com/java/technologies/downloads/) or [OpenJDK](https://openjdk.org/)

- **Maven 3.8+** for dependency management and build
    - Verify: `mvn -version`
    - Download: [Apache Maven](https://maven.apache.org/download.cgi)

## Installation

### As a Library Developer

1. Clone the repository:

```bash
git clone https://github.com/wutsi/ndopify-mono.git
cd ndopify-mono/modules/ndopify-dto
```

2. Build the library:

```bash
mvn clean install
```


### As a Consumer Application

Add the dependency to your Maven project:

```xml

<dependency>
    <groupId>com.wutsi.ndopify</groupId>
    <artifactId>ndopify-dto</artifactId>
    <version>0.0.281-SNAPSHOT</version>
</dependency>
```

For Gradle projects:

```gradle
implementation 'com.wutsi.ndopify:ndopify-dto:0.0.281-SNAPSHOT'
```

### GitHub Packages Authentication

Since ndopify-dto is published to GitHub Packages, configure authentication in your Maven `settings.xml`:

```xml

<settings>
    <servers>
        <server>
            <id>github</id>
            <username>YOUR_GITHUB_USERNAME</username>
            <password>YOUR_GITHUB_PERSONAL_ACCESS_TOKEN</password>
        </server>
    </servers>

    <profiles>
        <profile>
            <id>github</id>
            <repositories>
                <repository>
                    <id>github</id>
                    <url>https://maven.pkg.github.com/wutsi/ndopify-mono</url>
                </repository>
            </repositories>
        </profile>
    </profiles>

    <activeProfiles>
        <activeProfile>github</activeProfile>
    </activeProfiles>
</settings>
```
