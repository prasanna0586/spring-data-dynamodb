package org.socialsignin.spring.data.dynamodb.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.socialsignin.spring.data.dynamodb.domain.sample.Feed;
import org.socialsignin.spring.data.dynamodb.domain.sample.FeedPagingRepository;
import org.socialsignin.spring.data.dynamodb.repository.config.EnableDynamoDBRepositories;
import org.socialsignin.spring.data.dynamodb.utils.DynamoDBLocalResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.LocalDateTime;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { SortPageableDebugTest.TestAppConfig.class, DynamoDBLocalResource.class })
@TestPropertySource(properties = { "spring.data.dynamodb.entity2ddl.auto=create" })
public class SortPageableDebugTest {
    private final Random r = new Random();

    @Configuration
    @EnableDynamoDBRepositories(basePackages = "org.socialsignin.spring.data.dynamodb.domain.sample", marshallingMode = org.socialsignin.spring.data.dynamodb.core.MarshallingMode.SDK_V1_COMPATIBLE)
    public static class TestAppConfig {
    }

    @Autowired
    FeedPagingRepository feedPagingRepository;

    private Feed createFeed(String message) {
        Feed retValue = new Feed();
        retValue.setUserIdx(r.nextInt());
        retValue.setPaymentType(r.nextInt());
        retValue.setMessage(message);
        retValue.setRegDate(LocalDateTime.now());
        return retValue;
    }

    @Test
    public void test_save_only() {
        System.out.println("DEBUG: Starting save test");
        feedPagingRepository.save(createFeed("test1"));
        System.out.println("DEBUG: Saved first feed");
        feedPagingRepository.save(createFeed("test2"));
        System.out.println("DEBUG: Saved second feed");
        System.out.println("DEBUG: Save test completed");
    }

    @Test
    public void test_query_without_pagination() {
        System.out.println("DEBUG: Starting query test");
        feedPagingRepository.save(createFeed("me"));
        System.out.println("DEBUG: Saved feed");

        System.out.println("DEBUG: About to execute findAllByMessage without pagination");
        // Try a simple query first
        long count = feedPagingRepository.count();
        System.out.println("DEBUG: Total count: " + count);
        System.out.println("DEBUG: Query test completed");
    }

    @Test
    public void test_query_with_pagination() {
        System.out.println("DEBUG: Starting paginated query test");
        feedPagingRepository.save(createFeed("not yet me"));
        System.out.println("DEBUG: Saved feed 1");
        feedPagingRepository.save(createFeed("me"));
        System.out.println("DEBUG: Saved feed 2");

        System.out.println("DEBUG: About to execute findAllByMessageOrderByRegDateDesc with pagination");
        org.springframework.data.domain.PageRequest pageable = org.springframework.data.domain.PageRequest.of(0, 10);
        System.out.println("DEBUG: Created PageRequest");

        System.out.println("DEBUG: Calling findAllByMessageOrderByRegDateDesc...");
        org.springframework.data.domain.Page<Feed> actuals = feedPagingRepository.findAllByMessageOrderByRegDateDesc("me", pageable);
        System.out.println("DEBUG: Query returned");

        System.out.println("DEBUG: Getting total elements...");
        long totalElements = actuals.getTotalElements();
        System.out.println("DEBUG: Total elements: " + totalElements);
        System.out.println("DEBUG: Paginated query test completed");
    }
}
