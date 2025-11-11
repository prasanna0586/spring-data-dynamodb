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
package org.socialsignin.spring.data.dynamodb.utils;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.TestExecutionListener;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.awscore.client.builder.AwsSyncClientBuilder;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

@Configuration
public class DynamoDBLocalResource implements TestExecutionListener {

    private static final GenericContainer<?> dynamoDBContainer;

    static {
        dynamoDBContainer = new GenericContainer<>(DockerImageName.parse("amazon/dynamodb-local:latest"))
                .withExposedPorts(8000)
                .withCommand("-jar", "DynamoDBLocal.jar", "-inMemory", "-sharedDb");
        dynamoDBContainer.start();
    }

    @Bean
    public DynamoDbClient amazonDynamoDB() {
        String endpoint = String.format("http://%s:%d",
                dynamoDBContainer.getHost(),
                dynamoDBContainer.getMappedPort(8000));

        return DynamoDbClient.builder()
                .endpointOverride(
                        new AwsSyncClientBuilder.EndpointConfiguration(endpoint, "us-east-1"))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("dummy", "dummy")))
                .build();
    }

}
