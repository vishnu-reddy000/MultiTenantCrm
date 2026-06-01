package com.crm.demo.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.crm.demo.model.Team;
import com.crm.demo.model.User;

@Repository
public interface TeamRepository extends JpaRepository<Team, Long> {

    /** All teams for a tenant, ordered by name. */
    List<Team> findByTenantSegmentOrderByNameAsc(String tenantSegment);

    /** Find the team assigned to a specific manager. */
    Optional<Team> findByManager(User manager);

    /** Find the team(s) a user is a member of (within a tenant), with members and manager eagerly loaded. */
    @Query("SELECT DISTINCT t FROM Team t LEFT JOIN FETCH t.members LEFT JOIN FETCH t.manager WHERE :user MEMBER OF t.members AND t.tenantSegment = :tenant")
    List<Team> findByMemberAndTenant(@Param("user") User user, @Param("tenant") String tenant);

    /** Find the team(s) assigned to a specific manager, with members eagerly loaded. */
    @Query("SELECT DISTINCT t FROM Team t LEFT JOIN FETCH t.members LEFT JOIN FETCH t.manager WHERE t.manager = :manager")
    List<Team> findByManagerWithMembers(@Param("manager") User manager);
    boolean existsByNameAndTenantSegment(String name, String tenantSegment);

    /** Check if a team name already exists in a tenant (excluding a given id). */
    boolean existsByNameAndTenantSegmentAndIdNot(String name, String tenantSegment, Long id);
}
