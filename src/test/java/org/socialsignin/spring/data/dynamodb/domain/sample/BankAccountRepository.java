package org.socialsignin.spring.data.dynamodb.domain.sample;

import org.socialsignin.spring.data.dynamodb.repository.EnableScan;
import org.springframework.data.repository.CrudRepository;

import java.util.Optional;

/**
 * Repository for BankAccount entity.
 */
@EnableScan
public interface BankAccountRepository extends CrudRepository<BankAccount, String> {

    Optional<BankAccount> findByAccountHolder(String accountHolder);

    @EnableScan
    @Override
    long count();
}
