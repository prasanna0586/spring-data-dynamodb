package org.socialsignin.spring.data.dynamodb.domain.sample;

import org.socialsignin.spring.data.dynamodb.repository.EnableScan;
import org.socialsignin.spring.data.dynamodb.repository.EnableScanCount;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

/**
 * Repository for Task entity.
 */


public interface TaskRepository extends CrudRepository<Task, String> {

    @EnableScan
    List<Task> findByStatus(TaskStatus status);

    @EnableScan
    List<Task> findByPriority(Priority priority);

    @EnableScan
    List<Task> findByStatusAndPriority(TaskStatus status, Priority priority);

    @EnableScanCount
    long countByStatus(TaskStatus status);

    @EnableScan
    List<Task> findByStatusIn(List<TaskStatus> statuses);

    @EnableScan
    @Override
    long count();

    @EnableScan
    @Override
    Iterable<Task> findAll();

    @EnableScan
    @Override
    void deleteAll();
}
