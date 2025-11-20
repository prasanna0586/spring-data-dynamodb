package org.socialsignin.spring.data.dynamodb.domain.sample;

import org.socialsignin.spring.data.dynamodb.repository.EnableScan;
import org.socialsignin.spring.data.dynamodb.repository.EnableScanCount;
import org.springframework.data.repository.CrudRepository;

/**
 * Repository for BankAccount entity.
 */

public interface BankAccountRepository extends CrudRepository<BankAccount, String> {

    @EnableScanCount
    @Override
    long count();

    @EnableScan
    @Override
    void deleteAll();
}
