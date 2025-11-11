package org.socialsignin.spring.data.dynamodb.domain.sample;

import org.socialsignin.spring.data.dynamodb.repository.EnableScan;
import org.socialsignin.spring.data.dynamodb.repository.EnableScanCount;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

/**
 * Repository for Task entity.
 */
@EnableScan
@EnableScanCount
public interface TaskRepository extends CrudRepository<Task, String> {

    List<Task> findByStatus(TaskStatus status);

    List<Task> findByPriority(Priority priority);

    List<Task> findByStatusAndPriority(TaskStatus status, Priority priority);

    List<Task> findByAssignedTo(String assignedTo);

    long countByStatus(TaskStatus status);

    @Override
    long count();
}
