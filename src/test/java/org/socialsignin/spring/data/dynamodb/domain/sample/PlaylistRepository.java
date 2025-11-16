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

import org.socialsignin.spring.data.dynamodb.repository.EnableScan;
import org.socialsignin.spring.data.dynamodb.repository.Query;
import org.socialsignin.spring.data.dynamodb.repository.QueryConstants;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;

@EnableScan
public interface PlaylistRepository extends CrudRepository<Playlist, PlaylistId> {

    @Override
    @Query(consistentReads = QueryConstants.ConsistentReadMode.EVENTUAL)
    Optional<Playlist> findById(PlaylistId playlistId);

    // Range-key-only scan query
    List<Playlist> findByPlaylistName(String playlistName);

    // Scan-based exists queries
    boolean existsByDisplayName(String displayName);

    // Attribute override queries
    List<Playlist> findByDisplayName(String displayName);

    List<Playlist> findByUserNameAndDisplayName(String userName, String displayName);

    List<Playlist> findByPlaylistNameAndDisplayName(String playlistName, String displayName);
}
