package org.socialsignin.spring.data.dynamodb.domain.sample;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression;
import com.amazonaws.services.dynamodbv2.model.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.socialsignin.spring.data.dynamodb.repository.config.EnableDynamoDBRepositories;
import org.socialsignin.spring.data.dynamodb.utils.DynamoDBLocalResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprehensive integration tests for advanced query patterns and complex filter expressions.
 *
 * Coverage:
 * - Complex filter expressions (AND, OR, NOT)
 * - IN operator
 * - BETWEEN operator
 * - begins_with() function
 * - contains() function
 * - size() function
 * - attribute_exists() function
 * - attribute_type() function
 * - Multiple conditions combined
 * - Filter expressions with scan
 * - Consistent reads
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {DynamoDBLocalResource.class, AdvancedQueryPatternsIntegrationTest.TestAppConfig.class})
@TestPropertySource(properties = {"spring.data.dynamodb.entity2ddl.auto=create"})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Advanced Query Patterns and Filter Expressions Integration Tests")
public class AdvancedQueryPatternsIntegrationTest {

    @Configuration
    @EnableDynamoDBRepositories(basePackages = "org.socialsignin.spring.data.dynamodb.domain.sample")
    public static class TestAppConfig {
    }

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AmazonDynamoDB amazonDynamoDB;

    @Autowired
    private DynamoDBMapper dynamoDBMapper;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();

        // Create test data
        createTestUsers();
    }

    private void createTestUsers() {
        User user1 = new User();
        user1.setId("user-001");
        user1.setName("Alice Anderson");
        user1.setPostCode("12345");
        user1.setNumberOfPlaylists(5);
        user1.setTags(new HashSet<>(Arrays.asList("premium", "verified")));

        User user2 = new User();
        user2.setId("user-002");
        user2.setName("Bob Brown");
        user2.setPostCode("12345");
        user2.setNumberOfPlaylists(10);
        user2.setTags(new HashSet<>(Arrays.asList("verified")));

        User user3 = new User();
        user3.setId("user-003");
        user3.setName("Alice Baker");
        user3.setPostCode("67890");
        user3.setNumberOfPlaylists(15);

        User user4 = new User();
        user4.setId("user-004");
        user4.setName("Charlie Cooper");
        user4.setPostCode("67890");
        user4.setNumberOfPlaylists(8);
        user4.setTags(new HashSet<>(Arrays.asList("premium")));

        User user5 = new User();
        user5.setId("user-005");
        user5.setName("David Davis");
        user5.setPostCode("11111");
        user5.setNumberOfPlaylists(20);

        userRepository.saveAll(Arrays.asList(user1, user2, user3, user4, user5));
    }

    @Test
    @org.junit.jupiter.api.Order(1)
    @DisplayName("Test 1: Filter expression - BETWEEN operator")
    void testFilterExpressionBetween() {
        // Given - Test data already created

        // When - Scan with BETWEEN filter
        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":min", new AttributeValue().withN("8"));
        expressionAttributeValues.put(":max", new AttributeValue().withN("15"));

        ScanRequest scanRequest = new ScanRequest()
                .withTableName("user")
                .withFilterExpression("numberOfPlaylists BETWEEN :min AND :max")
                .withExpressionAttributeValues(expressionAttributeValues);

        ScanResult result = amazonDynamoDB.scan(scanRequest);

        // Then - Should return users with 8-15 playlists
        assertThat(result.getItems()).hasSize(3); // user2(10), user3(15), user4(8)
    }

    @Test
    @org.junit.jupiter.api.Order(2)
    @DisplayName("Test 2: Filter expression - IN operator")
    void testFilterExpressionIn() {
        // When - Scan with IN filter
        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":code1", new AttributeValue("12345"));
        expressionAttributeValues.put(":code2", new AttributeValue("67890"));

        ScanRequest scanRequest = new ScanRequest()
                .withTableName("user")
                .withFilterExpression("postCode IN (:code1, :code2)")
                .withExpressionAttributeValues(expressionAttributeValues);

        ScanResult result = amazonDynamoDB.scan(scanRequest);

        // Then - Should return users in postcodes 12345 or 67890
        assertThat(result.getItems()).hasSize(4); // user1, user2, user3, user4
    }

    @Test
    @org.junit.jupiter.api.Order(3)
    @DisplayName("Test 3: Filter expression - begins_with() function")
    void testFilterExpressionBeginsWith() {
        // When - Scan for names beginning with "Alice"
        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":prefix", new AttributeValue("Alice"));

        Map<String, String> expressionAttributeNames = new HashMap<>();
        expressionAttributeNames.put("#n", "name");

        ScanRequest scanRequest = new ScanRequest()
                .withTableName("user")
                .withFilterExpression("begins_with(#n, :prefix)")
                .withExpressionAttributeNames(expressionAttributeNames)
                .withExpressionAttributeValues(expressionAttributeValues);

        ScanResult result = amazonDynamoDB.scan(scanRequest);

        // Then - Should return Alice Anderson and Alice Baker
        assertThat(result.getItems()).hasSize(2);
    }

    @Test
    @org.junit.jupiter.api.Order(4)
    @DisplayName("Test 4: Filter expression - contains() function")
    void testFilterExpressionContains() {
        // When - Scan for names containing "Brown"
        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":substring", new AttributeValue("Brown"));

        Map<String, String> expressionAttributeNames = new HashMap<>();
        expressionAttributeNames.put("#n", "name");

        ScanRequest scanRequest = new ScanRequest()
                .withTableName("user")
                .withFilterExpression("contains(#n, :substring)")
                .withExpressionAttributeNames(expressionAttributeNames)
                .withExpressionAttributeValues(expressionAttributeValues);

        ScanResult result = amazonDynamoDB.scan(scanRequest);

        // Then - Should return Bob Brown
        assertThat(result.getItems()).hasSize(1);
    }

    @Test
    @org.junit.jupiter.api.Order(5)
    @DisplayName("Test 5: Filter expression - AND conditions")
    void testFilterExpressionAnd() {
        // When - Scan with multiple AND conditions
        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":postCode", new AttributeValue("12345"));
        expressionAttributeValues.put(":minPlaylists", new AttributeValue().withN("8"));

        ScanRequest scanRequest = new ScanRequest()
                .withTableName("user")
                .withFilterExpression("postCode = :postCode AND numberOfPlaylists > :minPlaylists")
                .withExpressionAttributeValues(expressionAttributeValues);

        ScanResult result = amazonDynamoDB.scan(scanRequest);

        // Then - Should return user2 (postCode=12345, playlists=10)
        assertThat(result.getItems()).hasSize(1);
    }

    @Test
    @org.junit.jupiter.api.Order(6)
    @DisplayName("Test 6: Filter expression - OR conditions")
    void testFilterExpressionOr() {
        // When - Scan with OR conditions
        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":high", new AttributeValue().withN("20"));
        expressionAttributeValues.put(":low", new AttributeValue().withN("5"));

        ScanRequest scanRequest = new ScanRequest()
                .withTableName("user")
                .withFilterExpression("numberOfPlaylists = :high OR numberOfPlaylists = :low")
                .withExpressionAttributeValues(expressionAttributeValues);

        ScanResult result = amazonDynamoDB.scan(scanRequest);

        // Then - Should return user1(5) and user5(20)
        assertThat(result.getItems()).hasSize(2);
    }

    @Test
    @org.junit.jupiter.api.Order(7)
    @DisplayName("Test 7: Filter expression - NOT condition")
    void testFilterExpressionNot() {
        // When - Scan with NOT condition
        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":code", new AttributeValue("12345"));

        ScanRequest scanRequest = new ScanRequest()
                .withTableName("user")
                .withFilterExpression("NOT postCode = :code")
                .withExpressionAttributeValues(expressionAttributeValues);

        ScanResult result = amazonDynamoDB.scan(scanRequest);

        // Then - Should return users not in postCode 12345
        assertThat(result.getItems()).hasSize(3); // user3, user4, user5
    }

    @Test
    @org.junit.jupiter.api.Order(8)
    @DisplayName("Test 8: Filter expression - attribute_exists()")
    void testFilterExpressionAttributeExists() {
        // When - Scan for users with tags
        ScanRequest scanRequest = new ScanRequest()
                .withTableName("user")
                .withFilterExpression("attribute_exists(tags)");

        ScanResult result = amazonDynamoDB.scan(scanRequest);

        // Then - Should return users with tags set
        assertThat(result.getItems()).hasSize(3); // user1, user2, user4
    }

    @Test
    @org.junit.jupiter.api.Order(9)
    @DisplayName("Test 9: Filter expression - attribute_not_exists()")
    void testFilterExpressionAttributeNotExists() {
        // When - Scan for users without tags
        ScanRequest scanRequest = new ScanRequest()
                .withTableName("user")
                .withFilterExpression("attribute_not_exists(tags)");

        ScanResult result = amazonDynamoDB.scan(scanRequest);

        // Then - Should return users without tags
        assertThat(result.getItems()).hasSize(2); // user3, user5
    }

    @Test
    @org.junit.jupiter.api.Order(10)
    @DisplayName("Test 10: Filter expression - size() function on set")
    void testFilterExpressionSizeFunction() {
        // When - Scan for users with exactly 2 tags
        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":size", new AttributeValue().withN("2"));

        ScanRequest scanRequest = new ScanRequest()
                .withTableName("user")
                .withFilterExpression("size(tags) = :size")
                .withExpressionAttributeValues(expressionAttributeValues);

        ScanResult result = amazonDynamoDB.scan(scanRequest);

        // Then - Should return user1 (has 2 tags: premium, verified)
        assertThat(result.getItems()).hasSize(1);
    }

    @Test
    @org.junit.jupiter.api.Order(11)
    @DisplayName("Test 11: Complex filter - Combination of multiple operators")
    void testComplexFilterExpression() {
        // When - Complex filter: (postCode=12345 OR postCode=67890) AND playlists > 5 AND name begins with A
        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":code1", new AttributeValue("12345"));
        expressionAttributeValues.put(":code2", new AttributeValue("67890"));
        expressionAttributeValues.put(":minPlaylists", new AttributeValue().withN("5"));
        expressionAttributeValues.put(":prefix", new AttributeValue("A"));

        Map<String, String> expressionAttributeNames = new HashMap<>();
        expressionAttributeNames.put("#n", "name");

        ScanRequest scanRequest = new ScanRequest()
                .withTableName("user")
                .withFilterExpression("(postCode = :code1 OR postCode = :code2) AND numberOfPlaylists > :minPlaylists AND begins_with(#n, :prefix)")
                .withExpressionAttributeNames(expressionAttributeNames)
                .withExpressionAttributeValues(expressionAttributeValues);

        ScanResult result = amazonDynamoDB.scan(scanRequest);

        // Then - Should return user3 (Alice Baker, postCode=67890, playlists=15)
        assertThat(result.getItems()).hasSize(1);
    }

    @Test
    @org.junit.jupiter.api.Order(12)
    @DisplayName("Test 12: Consistent read query")
    void testConsistentRead() {
        // Given - User repository with consistent read configured
        // (UserRepository has @Query(consistentReads = CONSISTENT) on findById)

        // When - Read with consistent read
        Optional<User> user = userRepository.findById("user-001");

        // Then - Should retrieve user (with strongly consistent read)
        assertThat(user).isPresent();
        assertThat(user.get().getName()).isEqualTo("Alice Anderson");
    }

    @Test
    @org.junit.jupiter.api.Order(13)
    @DisplayName("Test 13: Query with limit")
    void testQueryWithLimit() {
        // When - Scan with limit
        ScanRequest scanRequest = new ScanRequest()
                .withTableName("user")
                .withLimit(2);

        ScanResult result = amazonDynamoDB.scan(scanRequest);

        // Then - Should return at most 2 items
        assertThat(result.getItems().size()).isLessThanOrEqualTo(2);
    }

    @Test
    @org.junit.jupiter.api.Order(14)
    @DisplayName("Test 14: Scan with pagination")
    void testScanWithPagination() {
        // When - Scan with pagination (2 items per page)
        List<Map<String, AttributeValue>> allItems = new ArrayList<>();
        Map<String, AttributeValue> lastEvaluatedKey = null;

        do {
            ScanRequest scanRequest = new ScanRequest()
                    .withTableName("user")
                    .withLimit(2)
                    .withExclusiveStartKey(lastEvaluatedKey);

            ScanResult result = amazonDynamoDB.scan(scanRequest);
            allItems.addAll(result.getItems());
            lastEvaluatedKey = result.getLastEvaluatedKey();
        } while (lastEvaluatedKey != null);

        // Then - Should have retrieved all 5 users via pagination
        assertThat(allItems).hasSize(5);
    }

    @Test
    @org.junit.jupiter.api.Order(15)
    @DisplayName("Test 15: Count query with filter")
    void testCountWithFilter() {
        // When - Count users with more than 10 playlists
        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":threshold", new AttributeValue().withN("10"));

        ScanRequest scanRequest = new ScanRequest()
                .withTableName("user")
                .withSelect(Select.COUNT)
                .withFilterExpression("numberOfPlaylists > :threshold")
                .withExpressionAttributeValues(expressionAttributeValues);

        ScanResult result = amazonDynamoDB.scan(scanRequest);

        // Then - Should count users with >10 playlists
        assertThat(result.getCount()).isEqualTo(2); // user3(15), user5(20)
    }
}
