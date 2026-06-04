package com.crm.demo.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.crm.demo.model.LeaveRequest;
import com.crm.demo.model.User;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {

    List<LeaveRequest> findByEmployeeOrderByCreatedAtDesc(User employee);

    List<LeaveRequest> findByTenantSegmentOrderByCreatedAtDesc(String tenantSegment);

    List<LeaveRequest> findByTenantSegmentAndStatusOrderByCreatedAtDesc(String tenantSegment, String status);

    long countByEmployeeAndStatus(User employee, String status);

    long countByTenantSegmentAndStatus(String tenantSegment, String status);

    long countByTenantSegmentAndStatusAndFromDateLessThanEqualAndToDateGreaterThanEqual(
            String tenantSegment, String status, LocalDate dateForStart, LocalDate dateForEnd);

    long countByTenantSegmentAndStatusAndFromDateBetween(
            String tenantSegment, String status, LocalDate from, LocalDate to);
}
