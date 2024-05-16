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

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import org.springframework.data.repository.CrudRepository;

@DynamoDBTable(tableName = "nested_repo_test")
public class NestedRepostioryDocument {
    @DynamoDBHashKey
    public String hashKey;

    @DynamoDBAttribute
    public String someData;

    public NestedRepostioryDocument() {
    }

    public String getHashKey() { return hashKey; }
    public void setHashKey(String hashKey) {this.hashKey = hashKey; }

    public String getSomeData() {return someData; }
    public void setSomeData(String someData) { this.someData = someData; }

    public interface Repository extends CrudRepository<NestedRepostioryDocument, String> {
    }
}
