package com.crm.demo.repository;

import com.crm.demo.model.Report;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReportRepository extends JpaRepository<Report, Long> {

    /** All reports sent by a specific manager, newest first. */
    List<Report> findBySentByAndTenantSegmentOrderBySentAtDesc(String sentBy, String tenantSegment);

    /** All reports for a tenant, newest first (used by Admin/HR). */
    List<Report> findByTenantSegmentOrderBySentAtDesc(String tenantSegment);

    /**
     * Reports where the given user ID appears in the recipientIds CSV.
     * Uses LIKE to find the ID within the comma-separated string.
     */
    @Query("SELECT r FROM Report r WHERE r.tenantSegment = :tenant " +
           "AND (r.recipientIds = :id " +
           "OR r.recipientIds LIKE CONCAT(:id, ',%') " +
           "OR r.recipientIds LIKE CONCAT('%,', :id, ',%') " +
           "OR r.recipientIds LIKE CONCAT('%,', :id)) " +
           "ORDER BY r.sentAt DESC")
    List<Report> findByRecipientId(@Param("id") String id,
                                   @Param("tenant") String tenant);
}
