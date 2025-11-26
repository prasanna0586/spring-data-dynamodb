# Code Coverage

This project uses **JaCoCo** for code coverage reporting.

## Local Coverage Reports

Generate coverage report:
```bash
mvn clean verify
```

View the report:
```bash
open target/site/jacoco/index.html
```

### Report Formats

JaCoCo generates:
- **HTML**: `target/site/jacoco/index.html` - Interactive report
- **XML**: `target/site/jacoco/jacoco.xml` - For CI/CD
- **CSV**: `target/site/jacoco/jacoco.csv` - For data processing

## GitHub Actions

Coverage is automatically:
1. Generated on every push and PR
2. Published to [GitHub Pages](https://prasanna0586.github.io/spring-data-dynamodb/coverage/) (master only)

## Coverage Threshold

Minimum **70%** line coverage is required (configured in `pom.xml`).

To skip threshold check:
```bash
mvn clean test -Djacoco.skip=true
```

## Troubleshooting

**No report generated:** Run `mvn clean verify`, not just `mvn compile`.

**Coverage shows 0%:** Ensure tests are in `src/test/java` and actually running.
