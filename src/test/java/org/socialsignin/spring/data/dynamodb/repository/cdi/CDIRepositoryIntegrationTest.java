/**
 * Copyright © 2018 spring-data-dynamodb (https://github.com/prasanna0586/spring-data-dynamodb)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.socialsignin.spring.data.dynamodb.repository.cdi;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.se.SeContainer;
import jakarta.enterprise.inject.se.SeContainerInitializer;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.socialsignin.spring.data.dynamodb.core.DynamoDBOperations;
import org.socialsignin.spring.data.dynamodb.core.DynamoDBTemplate;
import org.socialsignin.spring.data.dynamodb.domain.sample.User;
import org.socialsignin.spring.data.dynamodb.domain.sample.UserRepository;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.net.URI;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for CDI (Contexts and Dependency Injection) support.
 * Tests repository bean discovery, injection, and lifecycle in a CDI container.
 *
 * This addresses the critical coverage gap in the CDI package (currently 24%).
 * Uses Jakarta CDI 4.1 and CDI SE container for testing.
 *
 * @author Prasanna Kumar Ramachandran
 */
public class CDIRepositoryIntegrationTest {

    private SeContainer container;
    private GenericContainer<?> dynamoDbContainer;

    /**
     * CDI Producer class that provides DynamoDB clients as CDI beans.
     * This simulates how a Jakarta EE application would configure DynamoDB clients.
     */
    @ApplicationScoped
    public static class DynamoDBProducer {

        private DynamoDbClient dynamoDbClient;
        private DynamoDbEnhancedClient enhancedClient;

        @Produces
        @ApplicationScoped
        public DynamoDbClient dynamoDbClient() {
            if (dynamoDbClient == null) {
                String endpoint = System.getProperty("dynamodb.endpoint");
                dynamoDbClient = DynamoDbClient.builder()
                        .region(Region.US_WEST_2)
                        .endpointOverride(URI.create(endpoint))
                        .credentialsProvider(StaticCredentialsProvider.create(
                                AwsBasicCredentials.create("test", "test")))
                        .build();
            }
            return dynamoDbClient;
        }

        @Produces
        @ApplicationScoped
        public DynamoDbEnhancedClient enhancedClient() {
            if (enhancedClient == null) {
                enhancedClient = DynamoDbEnhancedClient.builder()
                        .dynamoDbClient(dynamoDbClient())
                        .build();
            }
            return enhancedClient;
        }
    }

    /**
     * CDI bean that uses injected repository to test real-world usage patterns.
     */
    @ApplicationScoped
    public static class UserService {

        @Inject
        private UserRepository userRepository;

        public User createUser(String name) {
            User user = new User();
            user.setId(UUID.randomUUID().toString());
            user.setName(name);
            return userRepository.save(user);
        }

        public Optional<User> findUser(String id) {
            return userRepository.findById(id);
        }

        public long countUsers() {
            return userRepository.count();
        }

        public void deleteUser(String id) {
            userRepository.findById(id).ifPresent(userRepository::delete);
        }
    }

    @BeforeEach
    public void setUp() {
        // Start DynamoDB Local container
        dynamoDbContainer = new GenericContainer<>(DockerImageName.parse("amazon/dynamodb-local:latest"))
                .withExposedPorts(8000);
        dynamoDbContainer.start();

        String endpoint = String.format("http://%s:%d",
                dynamoDbContainer.getHost(),
                dynamoDbContainer.getMappedPort(8000));

        System.setProperty("dynamodb.endpoint", endpoint);

        // Initialize CDI SE container - beans.xml handles discovery
        container = SeContainerInitializer.newInstance()
                .initialize();

        // Create User table
        createUserTable();
    }

    @AfterEach
    public void tearDown() {
        if (container != null) {
            container.close();
        }
        if (dynamoDbContainer != null) {
            dynamoDbContainer.stop();
        }
        System.clearProperty("dynamodb.endpoint");
    }

    private void createUserTable() {
        DynamoDbEnhancedClient enhancedClient = container.select(DynamoDbEnhancedClient.class).get();
        DynamoDbTable<User> table = enhancedClient.table("User", TableSchema.fromBean(User.class));
        table.createTable();
    }

    @Test
    public void testCDIContainerInitialization() {
        // Test: CDI container should start successfully
        assertNotNull(container, "CDI container should be initialized");
        assertTrue(container.isRunning(), "CDI container should be running");
    }

    @Test
    public void testRepositoryBeanDiscovery() {
        // Test: DynamoDBRepositoryExtension should discover and register repository
        UserRepository repository = container.select(UserRepository.class).get();
        assertNotNull(repository, "UserRepository should be discovered and injected by CDI");
    }

    @Test
    public void testClientBeanInjection() {
        // Test: DynamoDB clients should be available as CDI beans
        DynamoDbClient client = container.select(DynamoDbClient.class).get();
        assertNotNull(client, "DynamoDbClient should be injectable");

        DynamoDbEnhancedClient enhancedClient = container.select(DynamoDbEnhancedClient.class).get();
        assertNotNull(enhancedClient, "DynamoDbEnhancedClient should be injectable");
    }

    @Test
    public void testRepositoryBasicCRUDOperations() {
        // Test: Repository CRUD operations through CDI injection
        UserRepository repository = container.select(UserRepository.class).get();

        // Create
        User user = new User();
        user.setId(UUID.randomUUID().toString());
        user.setName("CDI Test User");
        User savedUser = repository.save(user);
        assertNotNull(savedUser);
        assertEquals("CDI Test User", savedUser.getName());

        // Read
        Optional<User> foundUser = repository.findById(savedUser.getId());
        assertTrue(foundUser.isPresent());
        assertEquals("CDI Test User", foundUser.get().getName());

        // Update
        savedUser.setName("Updated CDI User");
        User updatedUser = repository.save(savedUser);
        assertEquals("Updated CDI User", updatedUser.getName());

        // Delete
        repository.delete(updatedUser);
        Optional<User> deletedUser = repository.findById(savedUser.getId());
        assertFalse(deletedUser.isPresent());
    }

    @Test
    public void testServiceWithInjectedRepository() {
        // Test: Service bean with injected repository should work
        UserService service = container.select(UserService.class).get();
        assertNotNull(service, "UserService should be injectable");

        // Create user through service
        User user = service.createUser("Service Test User");
        assertNotNull(user);
        assertNotNull(user.getId());
        assertEquals("Service Test User", user.getName());

        // Find user through service
        Optional<User> foundUser = service.findUser(user.getId());
        assertTrue(foundUser.isPresent());
        assertEquals("Service Test User", foundUser.get().getName());

        // Count users
        long count = service.countUsers();
        assertTrue(count >= 1);

        // Delete user through service
        service.deleteUser(user.getId());
        Optional<User> deletedUser = service.findUser(user.getId());
        assertFalse(deletedUser.isPresent());
    }

    @Test
    public void testMultipleConcurrentRepositoryAccess() {
        // Test: Multiple repository instances should work correctly
        UserRepository repository = container.select(UserRepository.class).get();

        // Create multiple users
        User user1 = new User();
        user1.setId(UUID.randomUUID().toString());
        user1.setName("User 1");

        User user2 = new User();
        user2.setId(UUID.randomUUID().toString());
        user2.setName("User 2");

        User user3 = new User();
        user3.setId(UUID.randomUUID().toString());
        user3.setName("User 3");

        repository.save(user1);
        repository.save(user2);
        repository.save(user3);

        // Verify all exist
        assertTrue(repository.findById(user1.getId()).isPresent());
        assertTrue(repository.findById(user2.getId()).isPresent());
        assertTrue(repository.findById(user3.getId()).isPresent());

        // Verify count
        long count = repository.count();
        assertTrue(count >= 3);
    }

    @Test
    public void testRepositoryBeanScope() {
        // Test: Repository should be application scoped (singleton behavior)
        UserRepository repo1 = container.select(UserRepository.class).get();
        UserRepository repo2 = container.select(UserRepository.class).get();

        // Should be same instance (application scoped)
        assertSame(repo1, repo2, "Repository should be application scoped (singleton)");
    }

    @Test
    public void testDynamoDBOperationsCreation() {
        // Test: DynamoDBTemplate should be created with injected clients
        DynamoDbClient client = container.select(DynamoDbClient.class).get();
        DynamoDbEnhancedClient enhancedClient = container.select(DynamoDbEnhancedClient.class).get();

        // Manually create DynamoDBOperations to verify it works
        DynamoDBOperations operations = new DynamoDBTemplate(client, enhancedClient, null, null);
        assertNotNull(operations);

        // Test operation
        User user = new User();
        user.setId(UUID.randomUUID().toString());
        user.setName("Operations Test");

        User saved = operations.save(user);
        assertNotNull(saved);

        User loaded = operations.load(User.class, saved.getId());
        assertNotNull(loaded);
        assertEquals("Operations Test", loaded.getName());
    }

    @Test
    public void testRepositoryQueryMethods() {
        // Test: Custom query methods work through CDI
        UserRepository repository = container.select(UserRepository.class).get();

        // Create test data
        User alice = new User();
        alice.setId(UUID.randomUUID().toString());
        alice.setName("Alice");
        repository.save(alice);

        User bob = new User();
        bob.setId(UUID.randomUUID().toString());
        bob.setName("Bob");
        repository.save(bob);

        // Test findAll
        Iterable<User> allUsers = repository.findAll();
        assertNotNull(allUsers);

        long count = 0;
        for (User ignored : allUsers) {
            count++;
        }
        assertTrue(count >= 2);
    }

    @Test
    public void testBatchOperationsThroughCDI() {
        // Test: Batch operations work through CDI-injected repository
        UserRepository repository = container.select(UserRepository.class).get();

        // Create multiple users
        User user1 = new User();
        user1.setId(UUID.randomUUID().toString());
        user1.setName("Batch User 1");

        User user2 = new User();
        user2.setId(UUID.randomUUID().toString());
        user2.setName("Batch User 2");

        // Save all
        Iterable<User> saved = repository.saveAll(java.util.Arrays.asList(user1, user2));
        assertNotNull(saved);

        // Verify saved
        assertTrue(repository.findById(user1.getId()).isPresent());
        assertTrue(repository.findById(user2.getId()).isPresent());
    }

    @Test
    public void testRepositoryExceptionHandling() {
        // Test: Exceptions are properly propagated through CDI proxy
        UserRepository repository = container.select(UserRepository.class).get();

        // Try to find non-existent user
        Optional<User> notFound = repository.findById("non-existent-id");
        assertFalse(notFound.isPresent());

        // Try to delete non-existent user (should throw exception)
        assertThrows(Exception.class, () -> {
            repository.deleteById("non-existent-id");
        });
    }
}
