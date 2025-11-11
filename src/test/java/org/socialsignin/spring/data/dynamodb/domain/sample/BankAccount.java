package org.socialsignin.spring.data.dynamodb.domain.sample;

import com.amazonaws.services.dynamodbv2.datamodeling.*;

import java.util.Objects;

/**
 * Domain model for testing transactional operations.
 * Represents a bank account for testing ACID transactions.
 */
@DynamoDBTable(tableName = "BankAccount")
public class BankAccount {

    private String accountId;
    private String accountHolder;
    private Double balance;
    private String status; // ACTIVE, FROZEN, CLOSED
    private Long version;

    public BankAccount() {
    }

    public BankAccount(String accountId, String accountHolder, Double balance) {
        this.accountId = accountId;
        this.accountHolder = accountHolder;
        this.balance = balance;
        this.status = "ACTIVE";
    }

    @DynamoDBHashKey(attributeName = "accountId")
    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    @DynamoDBAttribute(attributeName = "accountHolder")
    public String getAccountHolder() {
        return accountHolder;
    }

    public void setAccountHolder(String accountHolder) {
        this.accountHolder = accountHolder;
    }

    @DynamoDBAttribute(attributeName = "balance")
    public Double getBalance() {
        return balance;
    }

    public void setBalance(Double balance) {
        this.balance = balance;
    }

    @DynamoDBAttribute(attributeName = "status")
    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    @DynamoDBVersionAttribute
    @DynamoDBAttribute(attributeName = "version")
    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BankAccount that = (BankAccount) o;
        return Objects.equals(accountId, that.accountId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(accountId);
    }

    @Override
    public String toString() {
        return "BankAccount{" +
                "accountId='" + accountId + '\'' +
                ", accountHolder='" + accountHolder + '\'' +
                ", balance=" + balance +
                ", status='" + status + '\'' +
                ", version=" + version +
                '}';
    }
}
