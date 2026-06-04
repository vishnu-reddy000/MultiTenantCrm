package com.crm.demo.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.crm.demo.model.Task;

@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {

    /** All tasks for a specific tenant */
    List<Task> findByTenantSegment(String tenantSegment);

    /** All tasks assigned to a specific employee (by username) within a tenant */
    List<Task> findByAssignedToAndTenantSegment(String assignedTo, String tenantSegment);

    /** All tasks created by a specific manager within a tenant */
    List<Task> findByCreatedByAndTenantSegment(String createdBy, String tenantSegment);
}
