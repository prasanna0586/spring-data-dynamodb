# Code Coverage Setup

This project uses **JaCoCo** for code coverage reporting and **Codecov** for tracking coverage trends.

## Local Coverage Reports

### Generate Coverage Report

Run tests with coverage:
```bash
mvn clean test
```

Or run the full build (if tests pass):
```bash
mvn clean verify
```

If you have failing tests but still want the coverage report:
```bash
mvn clean test -Dmaven.test.failure.ignore=true
```

The coverage report will be generated at:
```
target/site/jacoco/index.html
```

Open in browser:
```bash
open target/site/jacoco/index.html  # macOS
xdg-open target/site/jacoco/index.html  # Linux
start target/site/jacoco/index.html  # Windows
```

### Coverage Report Formats

JaCoCo generates multiple report formats:
- **HTML**: `target/site/jacoco/index.html` - Interactive browsable report
- **XML**: `target/site/jacoco/jacoco.xml` - For CI/CD integration (Codecov)
- **CSV**: `target/site/jacoco/jacoco.csv` - For data processing

## GitHub Actions Integration

### Automatic Coverage Reporting

Coverage is automatically generated and uploaded on every push and pull request via the `runTests.yml` GitHub Actions workflow.

### Codecov Setup (One-time)

1. **Sign up for Codecov**:
   - Go to [codecov.io](https://codecov.io)
   - Sign in with your GitHub account
   - Add your repository

2. **Get Codecov Token**:
   - Go to Settings → General → Repository Upload Token
   - Copy the token

3. **Add Token to GitHub Secrets**:
   - Go to your GitHub repository
   - Settings → Secrets and variables → Actions
   - Click "New repository secret"
   - Name: `CODECOV_TOKEN`
   - Value: Paste the token from Codecov
   - Click "Add secret"

4. **Verify**:
   - Push a commit or create a pull request
   - Check GitHub Actions to see coverage upload
   - View coverage report on Codecov dashboard

### Coverage Artifacts

Every test run uploads the coverage report as a GitHub Actions artifact:
- Navigate to Actions → Select a workflow run
- Download "coverage-report" artifact
- Extract and open `index.html`

## Coverage Badges

Add to your README.md:

```markdown
[![codecov](https://codecov.io/gh/YOUR_USERNAME/spring-data-dynamodb/branch/YOUR_BRANCH/graph/badge.svg)](https://codecov.io/gh/YOUR_USERNAME/spring-data-dynamodb)
```

Replace `YOUR_USERNAME` and `YOUR_BRANCH` with your GitHub username and default branch.

## Coverage Thresholds

The project is configured with a minimum coverage threshold of **70%** for line coverage.

### Current Threshold Configuration

Located in `pom.xml`:
```xml
<limit>
    <counter>LINE</counter>
    <value>COVEREDRATIO</value>
    <minimum>0.70</minimum>
</limit>
```

### Adjust Thresholds

To change the minimum coverage requirement, update the `<minimum>` value in `pom.xml`.

To disable threshold checking temporarily:
```bash
mvn clean test -Djacoco.skip=true
```

## Viewing Coverage in Pull Requests

Once Codecov is set up:
- Codecov will automatically comment on pull requests with coverage changes
- See line-by-line coverage directly in PR diffs
- Track coverage trends over time

## Troubleshooting

### No coverage report generated

Make sure you run `mvn clean test` (not just `mvn compile`).

### Coverage report shows 0%

Check that:
1. Tests are actually running
2. JaCoCo agent is attached (check build logs)
3. Test classes are in `src/test/java`

### Codecov upload fails

1. Verify `CODECOV_TOKEN` is set in GitHub Secrets
2. Check that `jacoco.xml` exists after tests
3. Review GitHub Actions logs for error details

## Coverage Exclusions

To exclude specific classes/packages from coverage:
```xml
<configuration>
    <excludes>
        <exclude>**/config/**</exclude>
        <exclude>**/dto/**</exclude>
    </excludes>
</configuration>
```

Add this in the `jacoco-maven-plugin` configuration in `pom.xml`.
