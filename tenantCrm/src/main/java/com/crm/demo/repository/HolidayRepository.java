package com.crm.demo.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.crm.demo.model.Holiday;

@Repository
public interface HolidayRepository extends JpaRepository<Holiday, Long> {

    /** All holidays for a specific tenant. */
    List<Holiday> findByTenantSegmentOrderByDateAsc(String tenantSegment);

    /** Find a holiday by date AND tenant (prevents cross-tenant collision). */
    Optional<Holiday> findByDateAndTenantSegment(String date, String tenantSegment);

    /** All holidays for a tenant within a date range (dates stored as "YYYY-MM-DD" strings). */
    @org.springframework.data.jpa.repository.Query(
        "SELECT h FROM Holiday h WHERE h.tenantSegment = :tenant AND h.date >= :from AND h.date <= :to")
    List<Holiday> findByTenantAndDateRange(
        @org.springframework.data.repository.query.Param("tenant") String tenant,
        @org.springframework.data.repository.query.Param("from")   String from,
        @org.springframework.data.repository.query.Param("to")     String to);

    /** Check if a date is already taken for a tenant (excluding a given id). */
    boolean existsByDateAndTenantSegmentAndIdNot(String date, String tenantSegment, Long id);

    /** Check if a date is already taken for a tenant (for new records). */
    boolean existsByDateAndTenantSegment(String date, String tenantSegment);
}
