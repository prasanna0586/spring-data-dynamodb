# Docker Setup for Development

## Running Tests (Automatic)

Tests use **Testcontainers** which automatically manages Docker containers. Just ensure Docker Desktop is running:

```bash
# Make sure Docker Desktop is running, then:
mvn clean test
```

Testcontainers will:
- ✅ Automatically pull the `amazon/dynamodb-local` image
- ✅ Start a fresh container for each test run
- ✅ Clean up containers after tests complete

## Manual DynamoDB Local (Optional)

For manual testing or development, use docker-compose:

```bash
# Start DynamoDB Local
docker-compose up -d

# DynamoDB Local will be available at: http://localhost:8000

# Stop DynamoDB Local
docker-compose down
```

### Using AWS CLI with local DynamoDB:

```bash
# List tables
aws dynamodb list-tables --endpoint-url http://localhost:8000

# Create a table
aws dynamodb create-table \
    --table-name TestTable \
    --attribute-definitions AttributeName=id,AttributeType=S \
    --key-schema AttributeName=id,KeyType=HASH \
    --billing-mode PAY_PER_REQUEST \
    --endpoint-url http://localhost:8000
```

## Requirements

- Docker Desktop installed and running
- For tests: Docker will be used automatically by Testcontainers
- For manual use: Use `docker-compose up -d`
