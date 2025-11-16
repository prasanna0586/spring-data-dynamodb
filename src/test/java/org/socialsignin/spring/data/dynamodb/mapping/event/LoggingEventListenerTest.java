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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.socialsignin.spring.data.dynamodb.domain.sample.User;
import software.amazon.awssdk.core.pagination.sync.SdkIterable;
import software.amazon.awssdk.enhanced.dynamodb.model.PageIterable;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.*;

/**
 * Tests for LoggingEventListener.
 *
 * SDK v2 Migration Notes:
 * - SDK v1: PaginatedQueryList<T> → SDK v2: PageIterable<T>
 * - SDK v1: PaginatedScanList<T> → SDK v2: PageIterable<T>
 * - SDK v2: PageIterable.items() returns SdkIterable<T>
 * - SdkIterable.stream() provides iteration over all items across pages
 */
@ExtendWith(MockitoExtension.class)
public class LoggingEventListenerTest {

    private final User sampleEntity = new User();
    @Mock
    private PageIterable<User> sampleQueryList;
    @Mock
    private PageIterable<User> sampleScanList;
    @Mock
    private SdkIterable<User> sampleQueryItems;
    @Mock
    private SdkIterable<User> sampleScanItems;

    @Spy
    private LoggingEventListener underTest;

    @Test
    public void testAfterDelete() {
        underTest.onApplicationEvent(new AfterDeleteEvent<>(sampleEntity));

        verify(underTest).onAfterDelete(sampleEntity);
    }

    @Test
    public void testAfterLoad() {
        underTest.onApplicationEvent(new AfterLoadEvent<>(sampleEntity));

        verify(underTest).onAfterLoad(sampleEntity);
    }

    @Test
    public void testAfterQuery() {
        // SDK v2: PageIterable.items() returns SdkIterable which has stream() method
        List<User> queryList = new ArrayList<>();
        queryList.add(sampleEntity);

        when(sampleQueryList.items()).thenReturn(sampleQueryItems);
        when(sampleQueryItems.stream()).thenReturn(queryList.stream());

        underTest.onApplicationEvent(new AfterQueryEvent<>(sampleQueryList));

        verify(underTest).onAfterQuery(sampleEntity);
    }

    @Test
    public void testAfterSave() {
        underTest.onApplicationEvent(new AfterSaveEvent<>(sampleEntity));

        verify(underTest).onAfterSave(sampleEntity);
    }

    @Test
    public void testAfterScan() {
        // SDK v2: PageIterable.items() returns SdkIterable which has stream() method
        List<User> scanList = new ArrayList<>();
        scanList.add(sampleEntity);

        when(sampleScanList.items()).thenReturn(sampleScanItems);
        when(sampleScanItems.stream()).thenReturn(scanList.stream());

        underTest.onApplicationEvent(new AfterScanEvent<>(sampleScanList));

        verify(underTest).onAfterScan(sampleEntity);
    }

    @Test
    public void testBeforeDelete() {
        underTest.onApplicationEvent(new BeforeDeleteEvent<>(sampleEntity));

        verify(underTest).onBeforeDelete(sampleEntity);
    }

    @Test
    public void testBeforeSave() {
        underTest.onApplicationEvent(new BeforeSaveEvent<>(sampleEntity));

        verify(underTest).onBeforeSave(sampleEntity);
    }

}
