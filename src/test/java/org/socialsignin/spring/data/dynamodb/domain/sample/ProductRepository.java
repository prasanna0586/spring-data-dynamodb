package org.socialsignin.spring.data.dynamodb.domain.sample;

import org.socialsignin.spring.data.dynamodb.repository.EnableScan;
import org.springframework.data.repository.CrudRepository;

/**
 * Repository for Product entity.
 */
@EnableScan
public interface ProductRepository extends CrudRepository<Product, String> {

    @EnableScan
    @Override
    long count();
}
