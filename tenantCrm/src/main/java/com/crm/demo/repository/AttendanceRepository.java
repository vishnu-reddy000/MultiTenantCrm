package com.crm.demo.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.crm.demo.model.Attendance;
import com.crm.demo.model.User;

public interface AttendanceRepository extends JpaRepository<Attendance, Long> {

    /** All records for a tenant, newest first */
    List<Attendance> findByTenantSegmentOrderByDateDescCheckInDesc(String tenantSegment);

    /** Today's record for a specific user (used for punch-in/out state) */
    Optional<Attendance> findByUserAndDate(User user, LocalDate date);

    /** All records for a specific user */
    List<Attendance> findByUserOrderByDateDesc(User user);

    /** All records for a specific user within a date range, newest first */
    List<Attendance> findByUserAndDateBetweenOrderByDateDesc(User user, LocalDate from, LocalDate to);

    /** Count present days for a user */
    @Query("SELECT COUNT(a) FROM Attendance a WHERE a.user = :user AND a.status = 'present'")
    long countPresentDays(@Param("user") User user);

    /** Records for a tenant within a date range */
    @Query("SELECT a FROM Attendance a WHERE a.tenantSegment = :tenant " +
           "AND a.date BETWEEN :from AND :to ORDER BY a.date DESC")
    List<Attendance> findByTenantAndDateRange(@Param("tenant") String tenant,
                                              @Param("from")   LocalDate from,
                                              @Param("to")     LocalDate to);
}
