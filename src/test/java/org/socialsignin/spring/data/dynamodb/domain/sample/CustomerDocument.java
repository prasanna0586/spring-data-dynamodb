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
package org.socialsignin.spring.data.dynamodb.domain.sample;

import org.springframework.data.annotation.Id;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;


@DynamoDbBean
public class CustomerDocument {

    @Id
    private CustomerDocumentId customerDocumentId;

    private String s3Location;

    public CustomerDocument() {
    }

    public CustomerDocument(String customerId, String documentType, String version, String s3Location) {
        this.customerDocumentId = new CustomerDocumentId(customerId, documentType, version);
        this.s3Location = s3Location;
    }

    @DynamoDbPartitionKey
    @DynamoDbAttribute("customerId|documentType")
    public String getCustomerDocumentKey() {

        if (customerDocumentId == null) {
            return null;
        }

        return customerDocumentId.getCustomerDocumentKey();

    }

    public void setCustomerDocumentKey(String customerDocumentKey) {

        if (customerDocumentId == null) {
            this.customerDocumentId = new CustomerDocumentId();
        }

        customerDocumentId.setCustomerDocumentKey(customerDocumentKey);

    }

    @DynamoDbSortKey
    @DynamoDbAttribute("version")
    public String getVersion() {

        if (customerDocumentId == null) {
            return null;
        }

        return customerDocumentId.getVersion();

    }

    public void setVersion(String version) {

        if (customerDocumentId == null) {
            this.customerDocumentId = new CustomerDocumentId();
        }

        customerDocumentId.setVersion(version);

    }

    @DynamoDbIgnore
    public String getCustomerId() {

        if (customerDocumentId == null) {
            return null;
        }

        return customerDocumentId.getCustomerId();

    }

    @DynamoDbIgnore
    public String getDocumentType() {

        if (customerDocumentId == null) {
            return null;
        }

        return customerDocumentId.getDocumentType();

    }

    @DynamoDbIgnore
    public CustomerDocumentId getCustomerDocumentId() {
        return customerDocumentId;
    }

    public String getS3Location() {
        return s3Location;
    }

    public void setS3Location(String s3Location) {
        this.s3Location = s3Location;
    }

}
