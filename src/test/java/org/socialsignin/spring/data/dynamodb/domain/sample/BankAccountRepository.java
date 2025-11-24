package org.socialsignin.spring.data.dynamodb.domain.sample;

import org.socialsignin.spring.data.dynamodb.repository.EnableScan;
import org.springframework.data.repository.CrudRepository;

/**
 * Repository for BankAccount entity.
 */

public interface BankAccountRepository extends CrudRepository<BankAccount, String> {

    @EnableScan
    @Override
    long count();

    @EnableScan
    @Override
    Iterable<BankAccount> findAll();

    @EnableScan
    @Override
    void deleteAll();
}
