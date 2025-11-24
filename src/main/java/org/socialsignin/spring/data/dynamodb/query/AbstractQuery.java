/**
 * Copyright © 2018 spring-data-dynamodb (https://github.com/prasanna0586/spring-data-dynamodb)
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 *     http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.socialsignin.spring.data.dynamodb.query;

/**
 * Base abstract query class providing common query functionality for DynamoDB operations.
 * @param <T> the entity type
 * @author Prasanna Kumar Ramachandran
 */
public abstract class AbstractQuery<T> implements Query<T> {

    /**
     * Flag indicating whether scan operations are enabled for this query.
     */
    protected boolean scanEnabled = false;

    /**
     * Flag indicating whether scan count operations are enabled for this query.
     */
    protected boolean scanCountEnabled = false;

    @Override
    public boolean isScanCountEnabled() {
        return scanCountEnabled;
    }

    @Override
    public void setScanCountEnabled(boolean scanCountEnabled) {
        this.scanCountEnabled = scanCountEnabled;
    }

    @Override
    public void setScanEnabled(boolean scanEnabled) {
        this.scanEnabled = scanEnabled;
    }

    @Override
    public boolean isScanEnabled() {
        return scanEnabled;
    }

}
