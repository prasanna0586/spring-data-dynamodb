package org.socialsignin.spring.data.dynamodb.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { SortPageableDebugTest.TestAppConfig.class, DynamoDBLocalResource.class })
@TestPropertySource(properties = { "spring.data.dynamodb.entity2ddl.auto=create" })
public class SortPageableDebugTest {
    private final Random r = new Random();

    private static final Logger logger = LoggerFactory.getLogger(SortPageableDebugTest.class);

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
        logger.info("DEBUG: Starting save test");
        feedPagingRepository.save(createFeed("test1"));
        logger.info("DEBUG: Saved first feed");
        feedPagingRepository.save(createFeed("test2"));
        logger.info("DEBUG: Saved second feed");
        logger.info("DEBUG: Save test completed");
    }

    @Test
    public void test_query_without_pagination() {
        logger.info("DEBUG: Starting query test");
        feedPagingRepository.save(createFeed("me"));
        logger.info("DEBUG: Saved feed");

        logger.info("DEBUG: About to execute findAllByMessage without pagination");
        // Try a simple query first
        long count = feedPagingRepository.count();
        logger.info("DEBUG: Total count: " + count);
        logger.info("DEBUG: Query test completed");
    }

    @Test
    public void test_query_with_pagination() {
        logger.info("DEBUG: Starting paginated query test");
        feedPagingRepository.save(createFeed("not yet me"));
        logger.info("DEBUG: Saved feed 1");
        feedPagingRepository.save(createFeed("me"));
        logger.info("DEBUG: Saved feed 2");

        logger.info("DEBUG: About to execute findAllByMessageOrderByRegDateDesc with pagination");
        org.springframework.data.domain.PageRequest pageable = org.springframework.data.domain.PageRequest.of(0, 10);
        logger.info("DEBUG: Created PageRequest");

        logger.info("DEBUG: Calling findAllByMessageOrderByRegDateDesc...");
        org.springframework.data.domain.Page<Feed> actuals = feedPagingRepository.findAllByMessageOrderByRegDateDesc("me", pageable);
        logger.info("DEBUG: Query returned");

        logger.info("DEBUG: Getting total elements...");
        long totalElements = actuals.getTotalElements();
        logger.info("DEBUG: Total elements: " + totalElements);
        logger.info("DEBUG: Paginated query test completed");
    }
}
