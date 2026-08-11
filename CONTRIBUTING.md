# Contributing to PennyWise AI

Thank you for your interest in contributing to PennyWise AI! We welcome contributions from the community.

## How to Contribute

### 🐛 Reporting Bugs

1. Check if the bug has already been reported in [Issues](https://github.com/sarim2000/pennywiseai-tracker/issues)
2. If not, create a new issue using the bug report template
3. Include:
   - Device model and Android version
   - Steps to reproduce
   - Expected vs actual behavior
   - Screenshots if applicable
   - Bank name (if SMS parsing related)

### 💡 Suggesting Features

1. Check [existing issues](https://github.com/sarim2000/pennywiseai-tracker/issues) for similar suggestions
2. Create a new issue using the feature request template
3. Describe the problem it solves and how it would work

### 🏦 Adding Bank Support

To add support for a new bank:

1. Create a new parser class in `/app/src/main/java/com/pennywiseai/tracker/data/parser/bank/`
2. Extend the `BankParser` abstract class
3. Implement required methods:
   ```kotlin
   override fun getBankName(): String
   override fun canHandle(sender: String): Boolean
   override fun parse(smsBody: String, sender: String, timestamp: Long): ParsedTransaction?
   ```
4. Add your parser to `BankParserFactory.parsers` list
5. Test with real SMS samples

### 💻 Code Contributions

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Make your changes
4. Run tests: `./gradlew test`
5. Check code style: `./gradlew lint`
6. Commit using conventional commits:
   - `feat:` New feature
   - `fix:` Bug fix
   - `docs:` Documentation changes
   - `style:` Code style changes
   - `refactor:` Code refactoring
   - `test:` Test additions/changes
   - `chore:` Build/dependency updates
7. Push to your fork
8. Open a Pull Request

### 📋 Pull Request Guidelines

- Keep PRs focused on a single feature or fix
- Include tests for new functionality
- Update documentation if needed
- Ensure all tests pass
- Follow existing code style and patterns
- Add screenshots for UI changes

## Development Setup

### Prerequisites

- Android Studio Ladybug or newer
- JDK 11+
- Android SDK (API 31+)

### Building the Project

```bash
# Clone the repo
git clone https://github.com/sarim2000/pennywiseai-tracker.git
cd pennywiseai-tracker

# Build debug APK
./gradlew assembleDebug

# Run tests
./gradlew test

# Check code style
./gradlew lint
```

### Project Structure

```
app/
├── src/main/java/com/pennywiseai/tracker/
│   ├── data/
│   │   ├── database/      # Room database
│   │   ├── parser/        # SMS parsers
│   │   └── repository/    # Data repositories
│   ├── domain/            # Business logic
│   └── ui/               # Compose UI
└── build.gradle.kts
```

## Testing

- Test with real SMS messages from supported banks
- Test both light and dark themes
- Test on different screen sizes
- Verify offline functionality

## Community

- Join our [Discord](https://discord.gg/H3xWeMWjKQ) for discussions
- Follow development updates on [GitHub](https://github.com/sarim2000/pennywiseai-tracker)

## Code of Conduct

By participating, you agree to follow our [Code of Conduct](CODE_OF_CONDUCT.md).

Reporting concerns:
- Prefer a private report via Discord DM to a maintainer/moderator
- Or open a GitHub issue labeled `conduct` (maintainers will move details to a private channel)

## Recognition

All contributors will be recognized in our README following the [all-contributors](https://github.com/all-contributors/all-contributors) specification.

## Questions?

Feel free to:
- Open an issue for clarification
- Ask in our [Discord](https://discord.gg/H3xWeMWjKQ)
- Reach out to maintainers

Thank you for helping make PennyWise AI better! 🚀
