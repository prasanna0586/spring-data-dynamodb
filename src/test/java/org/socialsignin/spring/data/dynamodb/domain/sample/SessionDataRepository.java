package org.socialsignin.spring.data.dynamodb.domain.sample;

import org.socialsignin.spring.data.dynamodb.repository.EnableScan;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

/**
 * Repository for testing TTL (Time To Live) functionality.
 */
@EnableScan
public interface SessionDataRepository extends CrudRepository<SessionData, String> {

    List<SessionData> findByUserId(String userId);

    /**
     * Find sessions that have not yet expired (expirationTime greater than current time).
     */
    List<SessionData> findByExpirationTimeGreaterThan(Long currentTime);

    /**
     * Find sessions that have expired (expirationTime less than current time).
     */
    List<SessionData> findByExpirationTimeLessThan(Long currentTime);
}
