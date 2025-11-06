# Releasing to Maven Central

This guide explains how to release this library to Maven Central using the new Central Portal (OSSRH was sunset in June 2025).

## Prerequisites

### 1. Maven Central Account

1. Create an account at [Maven Central Portal](https://central.sonatype.com/)
2. Request access to the `io.github.prasanna0586` namespace (if not already granted)
3. Generate a User Token:
   - Go to Account → Generate User Token
   - Save the username and token securely

### 2. GPG Key Setup

You need a GPG key to sign your artifacts:

```bash
# Generate a new GPG key (if you don't have one)
gpg --gen-key

# List your keys
gpg --list-secret-keys --keyid-format=short

# Export your private key (for GitHub Actions)
gpg --armor --export-secret-keys YOUR_KEY_ID

# Publish your public key to a keyserver
gpg --keyserver keyserver.ubuntu.com --send-keys YOUR_KEY_ID
```

### 3. GitHub Secrets

Add the following secrets to your GitHub repository (Settings → Secrets and variables → Actions):

- `MAVEN_CENTRAL_USERNAME`: Your Maven Central user token username
- `MAVEN_CENTRAL_TOKEN`: Your Maven Central user token password
- `GPG_PRIVATE_KEY`: Your GPG private key (output from `gpg --armor --export-secret-keys YOUR_KEY_ID`)
- `GPG_PASSPHRASE`: Your GPG key passphrase

**Note:** The `actions/setup-java@v5` action automatically configures Maven's `settings.xml` with these credentials and sets up GPG signing. You don't need to manually configure anything else.

### 4. Local Maven Settings (for manual releases)

Create or update `~/.m2/settings.xml`:

```xml
<settings>
  <servers>
    <server>
      <id>central</id>
      <username>YOUR_MAVEN_CENTRAL_TOKEN_USERNAME</username>
      <password>YOUR_MAVEN_CENTRAL_TOKEN_PASSWORD</password>
    </server>
  </servers>
</settings>
```

## Testing Before Release

Before doing an actual release, it's recommended to test the publishing pipeline with a snapshot deployment.

### Test Snapshot Deployment (Safe)

1. Ensure your version ends with `-SNAPSHOT` (e.g., `6.0.5-SNAPSHOT`)
2. Go to Actions tab → "Run Snapshot Deploy" workflow
3. Click "Run workflow"
4. This will:
   - Run all tests
   - Build artifacts (JAR, sources, javadoc)
   - Sign with GPG
   - Deploy to Maven Central snapshot repository
   - **Does NOT create any release or tags**

5. Verify the snapshot appears at:
   - https://central.sonatype.com/artifact/io.github.prasanna0586/spring-data-dynamodb

**What this tests:**
- ✅ Maven Central authentication
- ✅ GPG signing
- ✅ Source and Javadoc JAR generation
- ✅ Artifact upload
- ✅ All release plugins work correctly

**What this does NOT do:**
- ❌ Create git tags
- ❌ Modify version numbers
- ❌ Create an official release

Once the snapshot deployment succeeds, you can proceed with confidence to either a release candidate or the actual release.

### If Snapshot Deploy Fails

Common issues and solutions:

**GPG Signing Fails:**
- Verify `GPG_PRIVATE_KEY` secret is correctly set
- Verify `GPG_PASSPHRASE` secret matches your key
- Check your GPG key is published: `gpg --keyserver keyserver.ubuntu.com --recv-keys YOUR_KEY_ID`

**Maven Central Authentication Fails:**
- Verify `MAVEN_CENTRAL_USERNAME` and `MAVEN_CENTRAL_TOKEN` are correct
- Test credentials at https://central.sonatype.com/
- Ensure you have access to the `io.github.prasanna0586` namespace

**Tests Fail:**
- Fix the tests before proceeding
- The release will also fail if tests fail

## Release Process

### Option 1: Release Candidate (For Major Changes)

Use this for major version bumps, breaking changes, or when testing a new release process.

1. Go to Actions tab → "Release Candidate" workflow
2. Click "Run workflow"
3. Enter RC number (e.g., `1` for RC1, `2` for RC2)
4. This will:
   - Validate the RC doesn't already exist
   - Create release `6.0.5-RC1` from `6.0.5-SNAPSHOT`
   - Publish to Maven Central
   - Create git tag `v6.0.5-RC1`
   - Reset back to `6.0.5-SNAPSHOT`

5. Manually create a GitHub Release:
   - Go to the provided URL in the workflow output
   - Mark as "pre-release"
   - Add release notes explaining what's being tested

6. Test the RC in your projects
7. If issues found: Fix them, then run RC2
8. If all good: Proceed to final release

**Example workflow:**
```
6.0.5-SNAPSHOT
    ↓
RC1 (test) → Found bugs
    ↓
Fix bugs in 6.0.5-SNAPSHOT
    ↓
RC2 (test) → All good!
    ↓
Final release 6.0.5
    ↓
6.0.6-SNAPSHOT
```

### Option 2: Direct Release (For Regular Updates)

Use this for bug fixes, minor improvements, or well-tested changes.

### Option 2a: GitHub Actions (Recommended)

1. Go to Actions tab in GitHub
2. Select "Release to Maven Central" workflow
3. Click "Run workflow"
4. (Optional) Override versions:
   - Leave empty to use automatic versioning (recommended)
   - Or specify custom release version (e.g., `7.0.0` for major version bump)
   - Or specify custom next development version (e.g., `7.0.1-SNAPSHOT`)
5. Click "Run workflow"

**Automatic versioning** (when inputs are empty):
- Current: `6.0.5-SNAPSHOT` → Release: `6.0.5` → Next: `6.0.6-SNAPSHOT`
- Current: `6.0.5-SNAPSHOT` → Release: `6.0.5` → Next: `6.1.0-SNAPSHOT` (minor bump)

The workflow will:
- Automatically determine versions from pom.xml (if not specified)
- Run all tests
- Create a release tag
- Build and sign artifacts
- Deploy to Maven Central
- Update version to next development version

### Option 2b: Manual Release

```bash
# Ensure everything is committed
git status

# Run the release
mvn release:prepare release:perform

# You'll be prompted for:
# - Release version (default: removes -SNAPSHOT)
# - SCM tag name (default: v{version})
# - Next development version (default: increments and adds -SNAPSHOT)
```

## What the Release Profile Does

The `release` profile (activated by maven-release-plugin) includes:

1. **central-publishing-maven-plugin**: Publishes to Maven Central Portal (replaces deprecated nexus-staging-maven-plugin)
2. **maven-gpg-plugin**: Signs all artifacts with your GPG key
3. **maven-source-plugin**: Creates source JAR
4. **maven-javadoc-plugin**: Creates Javadoc JAR

## Troubleshooting

### GPG Signing Issues

If you get "gpg: signing failed: Inappropriate ioctl for device":

```bash
export GPG_TTY=$(tty)
```

### Release Plugin Issues

If release:prepare fails, rollback and try again:

```bash
mvn release:rollback
```

### Maven Central Deployment Issues

Check your deployment status at:
- [Maven Central Portal](https://central.sonatype.com/)

## Version Naming

Follow Semantic Versioning:
- **MAJOR.MINOR.PATCH** (e.g., 6.0.5)
- Development versions end with `-SNAPSHOT` (e.g., 6.0.6-SNAPSHOT)

## Post-Release

After a successful release:
1. Check [Maven Central](https://central.sonatype.com/artifact/io.github.prasanna0586/spring-data-dynamodb) for the new version (may take a few hours to sync)
2. Create a GitHub Release with release notes
3. Update documentation if needed

## References

- [Maven Central Portal](https://central.sonatype.com/)
- [Maven Central Publishing Guide](https://central.sonatype.org/publish/publish-portal-maven/)
- [Maven Release Plugin Documentation](https://maven.apache.org/maven-release/maven-release-plugin/)
