# Build/Lint/Test Commands
- Build: ./gradlew assembleDebug
- Lint: ./gradlew lint
- Test: ./gradlew test
- Single test: ./gradlew test --tests <TestClassName>

# Code Style Guidelines
## Imports
- Use @Nullable/@NonNull annotations consistently
- Follow Android's import ordering (core, libraries, project)

## Formatting
- 4-space tabs
- 1 blank line between imports and package declaration
- Spacing around operators (a + b vs a+b)
- No auto-imports in IDEs
- No trailing spaces

## Naming
- ClassNamesCamelCase
- METHOD_NAME() for functions
- UPPER_CASE_FOR_CONSTANTS

## Error Handling
- Use custom exceptions when appropriate
- Comprehensive Logger setup with tag constants

# Project Dependencies
- AndroidX AppCompat 1.7.0+
- Material Components 1.12.0
- ConstraintLayout 2.2.0
- RootEncoder 2.7.2
- RTSP-Server 1.4.1
- ProGuard rules in app/proguard-rules.pro

# Key Files
- MainActivity.java: UI and navigation logic
- RtspServerService.java: RTSP stream handling
- build.gradle: Java 17 config
