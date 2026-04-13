# Build/Lint/Test Commands
- Build: ./gradlew assembleDebug
- Lint: ./gradlew lint
- Test: ./gradlew test
- Single test: ./gradlew test --tests <TestClassName>TestMethod?>

# Code Style Guidelines
## Imports
- Use @Nullable/@NonNull annotations consistently
- Follow Android's import ordering (core, libraries, project)

## Formatting
- 4-space tabs (android-standard-style)
- 1 blank line between imports and package declaration
- Spacing around operators (a + b vs a+b)
- No auto-imports in IDEs
- No trailing spaces

## Type Conventions
- Prefer val for constants, var for variables
- Use Data classes for immutable DTOs
- Package structure: feature/module/domain

## Naming
- ClassNamesCamelCase
- snake_case_for_variables
- METHOD_NAME() for functions
- UPPER_CASE_FOR_CONSTANTS

## Error Handling
- Use sealed classes for API responses (e.g., Result<T>)
- Comprehensive Logger setup with tag constants
- Custom exceptions (e.g., NetworkException)

# Cursor/Copilot Rules
- Follow existing .cursor/rules and .github/copilot-instructions.md
- Prioritize: 
  1. .cursorrules (if exists)
  2. Copilot instructions (if exists)
- Current rules:
  - Use Android's recommended practices
  - Maintain RTL support
  - Maintain multiDex configuration

# Project Dependencies
- AndroidX 1.6.1+
- CameraX 1.3.1
- RootEncoder 2.7.2
- RTSP-Server 1.4.1
- Kotlin 1.9.22
- ProGuard rules in app/proguard-rules.pro

# Key Files
- MainActivity.kt: UI and navigation logic
- RtspServerService.kt: RTSP stream handling
- NetworkMonitor.kt: Quality adaptation
- build.gradle: MultiDex + Java 17 config