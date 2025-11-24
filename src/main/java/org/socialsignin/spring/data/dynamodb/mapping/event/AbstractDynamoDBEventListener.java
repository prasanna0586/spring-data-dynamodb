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
package org.socialsignin.spring.data.dynamodb.mapping.event;

/*
 * Copyright 2014 by the original author(s).
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationListener;
import org.springframework.core.GenericTypeResolver;
import org.springframework.lang.NonNull;
import software.amazon.awssdk.enhanced.dynamodb.model.PageIterable;

import java.util.function.Consumer;
import java.util.stream.StreamSupport;

/**
 * Base class to implement domain class specific {@link ApplicationListener}s.
 * @author Prasanna Kumar Ramachandran
 */
public abstract class AbstractDynamoDBEventListener<E> implements ApplicationListener<DynamoDBMappingEvent<?>> {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractDynamoDBEventListener.class);
    @NonNull
    private final Class<?> domainClass;

    /**
     * Creates a new {@link AbstractDynamoDBEventListener}.
     */
    public AbstractDynamoDBEventListener() {
        Class<?> typeArgument = GenericTypeResolver.resolveTypeArgument(this.getClass(),
                AbstractDynamoDBEventListener.class);
        this.domainClass = typeArgument == null ? Object.class : typeArgument;
    }

    @NonNull
    protected Class<?> getDomainClass() {
        return this.domainClass;
    }

    /*
     * (non-Javadoc)
     * @see org.springframework.context.ApplicationListener#onApplicationEvent(org
     * .springframework.context.ApplicationEvent)
     */
    @Override
    public void onApplicationEvent(@NonNull DynamoDBMappingEvent<?> event) {

        @SuppressWarnings("unchecked")
        E source = (E) event.getSource();

        // source can not be null as java.util.EventObject can not be constructed with
        // null
        assert source != null;

        if (event instanceof AfterScanEvent) {

            publishEachElement((PageIterable<?>) source, this::onAfterScan);
            return;
        } else if (event instanceof AfterQueryEvent) {

            publishEachElement((PageIterable<?>) source, this::onAfterQuery);
            return;
        }
        // Check for matching domain type and invoke callbacks
        else if (domainClass.isAssignableFrom(source.getClass())) {
            switch (event) {
                case BeforeSaveEvent ignored -> {
                    onBeforeSave(source);
                    return;
                }
                case AfterSaveEvent ignored -> {
                    onAfterSave(source);
                    return;
                }
                case BeforeDeleteEvent ignored -> {
                    onBeforeDelete(source);
                    return;
                }
                case AfterDeleteEvent ignored -> {
                    onAfterDelete(source);
                    return;
                }
                case AfterLoadEvent ignored -> {
                    onAfterLoad(source);
                    return;
                }
                default -> {
                }
            }
        }
        // we should never end up here
        assert false;
    }

    @SuppressWarnings("unchecked")
    private void publishEachElement(@NonNull PageIterable<?> pageIterable, Consumer<E> publishMethod) {
        StreamSupport.stream(pageIterable.items().spliterator(), false)
                .filter(o -> domainClass.isAssignableFrom(o.getClass()))
                .map(o -> (E) o)
                .forEach(publishMethod);
    }

    public void onBeforeSave(E source) {
        LOG.debug("onBeforeSave({})", source);
    }

    public void onAfterSave(E source) {
        LOG.debug("onAfterSave({})", source);
    }

    public void onAfterLoad(E source) {
        LOG.debug("onAfterLoad({})", source);
    }

    public void onAfterDelete(E source) {
        LOG.debug("onAfterDelete({})", source);
    }

    public void onBeforeDelete(E source) {
        LOG.debug("onBeforeDelete({})", source);
    }

    public void onAfterScan(E source) {
        LOG.debug("onAfterScan({})", source);
    }

    public void onAfterQuery(E source) {
        LOG.debug("onAfterQuery({})", source);
    }

}
