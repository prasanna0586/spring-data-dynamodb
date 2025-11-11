package org.socialsignin.spring.data.dynamodb.domain.sample;

import org.socialsignin.spring.data.dynamodb.repository.EnableScan;
import org.springframework.data.repository.CrudRepository;

/**
 * Repository for CustomerOrder entity.
 */
@EnableScan
public interface CustomerOrderRepository extends CrudRepository<CustomerOrder, String> {

    @EnableScan
    @Override
    long count();
}
