package com.crm.demo.repository;

import com.crm.demo.model.PerformanceReview;
import com.crm.demo.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PerformanceReviewRepository extends JpaRepository<PerformanceReview, Long> {

    /** All reviews for a tenant, newest first */
    List<PerformanceReview> findByTenantSegmentOrderByReviewedAtDesc(String tenantSegment);

    /** All reviews for a specific employee */
    List<PerformanceReview> findByEmployeeOrderByReviewMonthDesc(User employee);

    /** Find an existing review for a specific employee + month + tenant (for upsert) */
    Optional<PerformanceReview> findByEmployeeAndReviewMonthAndTenantSegment(
            User employee, String reviewMonth, String tenantSegment);

    /** All reviews written by a specific manager */
    List<PerformanceReview> findByReviewedByAndTenantSegmentOrderByReviewedAtDesc(
            String reviewedBy, String tenantSegment);
}
