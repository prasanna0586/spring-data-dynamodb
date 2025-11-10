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

import com.amazonaws.services.dynamodbv2.datamodeling.PaginatedQueryList;
import com.amazonaws.services.dynamodbv2.datamodeling.PaginatedScanList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.socialsignin.spring.data.dynamodb.domain.sample.User;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class LoggingEventListenerTest {

    private final User sampleEntity = new User();
    @Mock
    private PaginatedQueryList<User> sampleQueryList;
    @Mock
    private PaginatedScanList<User> sampleScanList;

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
        List<User> queryList = new ArrayList<>();
        queryList.add(sampleEntity);
        when(sampleQueryList.stream()).thenReturn(queryList.stream());

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
        List<User> scanList = new ArrayList<>();
        scanList.add(sampleEntity);
        when(sampleScanList.stream()).thenReturn(scanList.stream());

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
