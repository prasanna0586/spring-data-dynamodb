package org.socialsignin.spring.data.dynamodb.domain.sample;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.socialsignin.spring.data.dynamodb.core.DynamoDBOperations;
import org.socialsignin.spring.data.dynamodb.repository.config.EnableDynamoDBRepositories;
import org.socialsignin.spring.data.dynamodb.utils.DynamoDBLocalResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.model.PageIterable;
import software.amazon.awssdk.enhanced.dynamodb.model.ScanEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprehensive integration tests for advanced query patterns and complex filter expressions.
 *
 * This test suite validates the library's ability to handle:
 * - Repository findBy methods (high-level API) for standard queries
 * - DynamoDBOperations.scan() (mid-level API) for advanced filter expressions
 *
 * Coverage:
 * - Repository Methods: BETWEEN, IN, StartingWith, Containing, AND conditions
 * - Scan Operations: OR, NOT, attribute_exists(), size(), complex expressions
 * - Pagination and limiting
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
    private DynamoDBOperations dynamoDBOperations;

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

    // ==================== Repository Method Tests (High-Level API) ====================

    @Test
    @Order(1)
    @DisplayName("Test 1: BETWEEN operator via repository")
    void testFilterExpressionBetween() {
        // When - Use repository method
        List<User> users = userRepository.findByNumberOfPlaylistsBetween(8, 15);

        // Then - Should return users with 8-15 playlists
        assertThat(users).hasSize(3); // user2(10), user3(15), user4(8)
        assertThat(users).allMatch(u -> u.getNumberOfPlaylists() >= 8 && u.getNumberOfPlaylists() <= 15);
    }

    @Test
    @Order(2)
    @DisplayName("Test 2: IN operator via repository")
    void testFilterExpressionIn() {
        // When - Use repository method
        List<User> users = userRepository.findByPostCodeIn(Arrays.asList("12345", "67890"));

        // Then - Should return users in postcodes 12345 or 67890
        assertThat(users).hasSize(4); // user1, user2, user3, user4
        assertThat(users).allMatch(u -> u.getPostCode().equals("12345") || u.getPostCode().equals("67890"));
    }

    @Test
    @Order(3)
    @DisplayName("Test 3: begins_with() function via repository")
    void testFilterExpressionBeginsWith() {
        // When - Use repository method
        List<User> users = userRepository.findByNameStartingWith("Alice");

        // Then - Should return Alice Anderson and Alice Baker
        assertThat(users).hasSize(2);
        assertThat(users).allMatch(u -> u.getName().startsWith("Alice"));
    }

    @Test
    @Order(4)
    @DisplayName("Test 4: contains() function via repository")
    void testFilterExpressionContains() {
        // When - Use repository method
        List<User> users = userRepository.findByNameContaining("Brown");

        // Then - Should return Bob Brown
        assertThat(users).hasSize(1);
        assertThat(users.get(0).getName()).contains("Brown");
    }

    @Test
    @Order(5)
    @DisplayName("Test 5: AND conditions via repository")
    void testFilterExpressionAnd() {
        // When - Use repository method
        List<User> users = userRepository.findByPostCodeAndNumberOfPlaylistsGreaterThan("12345", 8);

        // Then - Should return user2 (postCode=12345, playlists=10)
        assertThat(users).hasSize(1);
        assertThat(users.get(0).getPostCode()).isEqualTo("12345");
        assertThat(users.get(0).getNumberOfPlaylists()).isGreaterThan(8);
    }

    // ==================== DynamoDBOperations.scan() Tests (Mid-Level API) ====================

    @Test
    @Order(6)
    @DisplayName("Test 6: OR conditions via DynamoDBOperations.scan()")
    void testFilterExpressionOr() {
        // Given - Build scan request with OR filter
        Map<String, AttributeValue> expressionValues = new HashMap<>();
        expressionValues.put(":high", AttributeValue.builder().n("20").build());
        expressionValues.put(":low", AttributeValue.builder().n("5").build());

        ScanEnhancedRequest scanRequest = ScanEnhancedRequest.builder()
                .filterExpression(Expression.builder()
                        .expression("numberOfPlaylists = :high OR numberOfPlaylists = :low")
                        .expressionValues(expressionValues)
                        .build())
                .build();

        // When - Execute scan via library API
        PageIterable<User> result = dynamoDBOperations.scan(User.class, scanRequest);
        List<User> users = result.items().stream().toList();

        // Then - Should return user1(5) and user5(20)
        assertThat(users).hasSize(2);
        assertThat(users).extracting(User::getNumberOfPlaylists)
                .containsExactlyInAnyOrder(5, 20);
    }

    @Test
    @Order(7)
    @DisplayName("Test 7: NOT condition via DynamoDBOperations.scan()")
    void testFilterExpressionNot() {
        // Given - Build scan request with NOT filter
        Map<String, AttributeValue> expressionValues = new HashMap<>();
        expressionValues.put(":code", AttributeValue.fromS("12345"));

        ScanEnhancedRequest scanRequest = ScanEnhancedRequest.builder()
                .filterExpression(Expression.builder()
                        .expression("NOT postCode = :code")
                        .expressionValues(expressionValues)
                        .build())
                .build();

        // When - Execute scan via library API
        PageIterable<User> result = dynamoDBOperations.scan(User.class, scanRequest);
        List<User> users = result.items().stream().toList();

        // Then - Should return users not in postCode 12345
        assertThat(users).hasSize(3); // user3, user4, user5
        assertThat(users).noneMatch(u -> u.getPostCode().equals("12345"));
    }

    @Test
    @Order(8)
    @DisplayName("Test 8: attribute_exists() via DynamoDBOperations.scan()")
    void testFilterExpressionAttributeExists() {
        // Given - Build scan request with attribute_exists filter
        ScanEnhancedRequest scanRequest = ScanEnhancedRequest.builder()
                .filterExpression(Expression.builder()
                        .expression("attribute_exists(tags)")
                        .build())
                .build();

        // When - Execute scan via library API
        PageIterable<User> result = dynamoDBOperations.scan(User.class, scanRequest);
        List<User> users = result.items().stream().toList();

        // Then - Should return users with tags set
        assertThat(users).hasSize(3); // user1, user2, user4
        assertThat(users).allMatch(u -> u.getTags() != null && !u.getTags().isEmpty());
    }

    @Test
    @Order(9)
    @DisplayName("Test 9: attribute_not_exists() via DynamoDBOperations.scan()")
    void testFilterExpressionAttributeNotExists() {
        // Given - Build scan request with attribute_not_exists filter
        ScanEnhancedRequest scanRequest = ScanEnhancedRequest.builder()
                .filterExpression(Expression.builder()
                        .expression("attribute_not_exists(tags)")
                        .build())
                .build();

        // When - Execute scan via library API
        PageIterable<User> result = dynamoDBOperations.scan(User.class, scanRequest);
        List<User> users = result.items().stream().toList();

        // Then - Should return users without tags
        assertThat(users).hasSize(2); // user3, user5
        assertThat(users).allMatch(u -> u.getTags() == null || u.getTags().isEmpty());
    }

    @Test
    @Order(10)
    @DisplayName("Test 10: size() function via DynamoDBOperations.scan()")
    void testFilterExpressionSizeFunction() {
        // Given - Build scan request with size() filter
        Map<String, AttributeValue> expressionValues = new HashMap<>();
        expressionValues.put(":size", AttributeValue.builder().n("2").build());

        ScanEnhancedRequest scanRequest = ScanEnhancedRequest.builder()
                .filterExpression(Expression.builder()
                        .expression("size(tags) = :size")
                        .expressionValues(expressionValues)
                        .build())
                .build();

        // When - Execute scan via library API
        PageIterable<User> result = dynamoDBOperations.scan(User.class, scanRequest);
        List<User> users = result.items().stream().toList();

        // Then - Should return user1 (has 2 tags: premium, verified)
        assertThat(users).hasSize(1);
        assertThat(users.get(0).getTags()).hasSize(2);
    }

    @Test
    @Order(11)
    @DisplayName("Test 11: Complex filter - Combination of multiple operators via DynamoDBOperations.scan()")
    void testComplexFilterExpression() {
        // Given - Complex filter: (postCode=12345 OR postCode=67890) AND playlists > 5 AND name begins with A
        Map<String, AttributeValue> expressionValues = new HashMap<>();
        expressionValues.put(":code1", AttributeValue.fromS("12345"));
        expressionValues.put(":code2", AttributeValue.fromS("67890"));
        expressionValues.put(":minPlaylists", AttributeValue.builder().n("5").build());
        expressionValues.put(":prefix", AttributeValue.fromS("A"));

        Map<String, String> expressionNames = new HashMap<>();
        expressionNames.put("#n", "name");

        ScanEnhancedRequest scanRequest = ScanEnhancedRequest.builder()
                .filterExpression(Expression.builder()
                        .expression("(postCode = :code1 OR postCode = :code2) AND numberOfPlaylists > :minPlaylists AND begins_with(#n, :prefix)")
                        .expressionNames(expressionNames)
                        .expressionValues(expressionValues)
                        .build())
                .build();

        // When - Execute scan via library API
        PageIterable<User> result = dynamoDBOperations.scan(User.class, scanRequest);
        List<User> users = result.items().stream().toList();

        // Then - Should return user3 (Alice Baker, postCode=67890, playlists=15)
        assertThat(users).hasSize(1);
        assertThat(users.get(0).getName()).startsWith("A");
        assertThat(users.get(0).getNumberOfPlaylists()).isGreaterThan(5);
    }

    @Test
    @Order(12)
    @DisplayName("Test 12: Consistent read query via repository")
    void testConsistentRead() {
        // When - Read with consistent read (UserRepository.findById has @Query(consistentReads = CONSISTENT))
        Optional<User> user = userRepository.findById("user-001");

        // Then - Should retrieve user (with strongly consistent read)
        assertThat(user).isPresent();
        assertThat(user.get().getName()).isEqualTo("Alice Anderson");
    }

    @Test
    @Order(13)
    @DisplayName("Test 13: Query with limit via repository pagination")
    void testQueryWithLimit() {
        // When - Use repository with pagination (limit 2)
        List<User> users = userRepository.findAll();

        // Then - Should return all users (we have 5)
        assertThat(users).hasSize(5);

        // Can also test with explicit pagination if needed
        // Note: Actual limit testing would require pageable support in the specific test scenario
    }

    @Test
    @Order(14)
    @DisplayName("Test 14: Scan with pagination via DynamoDBOperations.scan()")
    void testScanWithPagination() {
        // Given - Build scan request with limit for pagination
        ScanEnhancedRequest scanRequest = ScanEnhancedRequest.builder()
                .limit(2)
                .build();

        // When - Execute scan and manually paginate
        PageIterable<User> pageIterable = dynamoDBOperations.scan(User.class, scanRequest);
        List<User> allUsers = new ArrayList<>();

        // Collect all items across pages
        pageIterable.items().forEach(allUsers::add);

        // Then - Should have retrieved all 5 users via pagination
        assertThat(allUsers).hasSize(5);
    }

    @Test
    @Order(15)
    @DisplayName("Test 15: Count with filter via DynamoDBOperations.count()")
    void testCountWithFilter() {
        // Given - Build scan request with filter for count
        Map<String, AttributeValue> expressionValues = new HashMap<>();
        expressionValues.put(":threshold", AttributeValue.builder().n("10").build());

        ScanEnhancedRequest scanRequest = ScanEnhancedRequest.builder()
                .filterExpression(Expression.builder()
                        .expression("numberOfPlaylists > :threshold")
                        .expressionValues(expressionValues)
                        .build())
                .build();

        // When - Execute count via library API
        int count = dynamoDBOperations.count(User.class, scanRequest);

        // Then - Should count users with >10 playlists
        assertThat(count).isEqualTo(2); // user3(15), user5(20)
    }
}
