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
package org.socialsignin.spring.data.dynamodb.repository.config;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.FactoryBean;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

public class DynamoDBMapperFactory implements FactoryBean<DynamoDBMapper>, BeanFactoryAware {

    private BeanFactory beanFactory;

    @Override
    public DynamoDBMapper getObject() throws Exception {
        DynamoDbClient amazonDynamoDB = beanFactory.getBean(DynamoDbClient.class);
        DynamoDBMapperConfig dynamoDBMapperConfig = beanFactory.getBean(DynamoDBMapperConfig.class);
        return new DynamoDBMapper(amazonDynamoDB, dynamoDBMapperConfig);
    }

    @Override
    public Class<?> getObjectType() {
        return DynamoDBMapper.class;
    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        this.beanFactory = beanFactory;
    }

}
