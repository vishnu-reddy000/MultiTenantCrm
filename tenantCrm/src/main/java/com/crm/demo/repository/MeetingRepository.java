package com.crm.demo.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.crm.demo.model.Meeting;

public interface MeetingRepository extends JpaRepository<Meeting, Long> {

	List<Meeting> findByTenantSegmentAndMeetingDateGreaterThanEqualOrderByMeetingDateAscMeetingTimeAsc(
			String tenantSegment, LocalDate date);

	/** All meetings for a tenant on a specific date, ordered by time. */
	List<Meeting> findByTenantSegmentAndMeetingDateOrderByMeetingTimeAsc(
			String tenantSegment, LocalDate date);

	/** All meetings for a tenant on a specific date where the participant username appears. */
	@Query("SELECT m FROM Meeting m WHERE m.tenantSegment = :tenant " +
	       "AND m.participants LIKE %:username% " +
	       "AND m.meetingDate = :date " +
	       "ORDER BY m.meetingTime ASC")
	List<Meeting> findTodayMeetingsForParticipant(
			@Param("tenant") String tenant,
			@Param("username") String username,
			@Param("date") LocalDate date);

	/** Find all meetings in a tenant where the given username appears in the participants field. */
	@Query("SELECT m FROM Meeting m WHERE m.tenantSegment = :tenant AND m.participants LIKE %:username% AND m.meetingDate >= :date")
	List<Meeting> findByTenantAndParticipantUsernameAndMeetingDateGreaterThanEqual(
			@Param("tenant") String tenant,
			@Param("username") String username,
			@Param("date") LocalDate date);

	/** Find every meeting for a user or host in the tenant, ordered newest first. */
	@Query("SELECT m FROM Meeting m WHERE m.tenantSegment = :tenant " +
	       "AND (m.participants LIKE %:username% OR m.scheduledBy = :username) " +
	       "ORDER BY m.meetingDate DESC, m.meetingTime DESC")
	List<Meeting> findAllMeetingsForUserOrHost(
			@Param("tenant") String tenant,
			@Param("username") String username);

	/**
	 * Find all upcoming meetings in a tenant where the user is either:
	 *  - a participant (username appears in the participants field), OR
	 *  - the host who scheduled the meeting (scheduledBy = username)
	 * Results are ordered by date then time ascending.
	 */
	@Query("SELECT m FROM Meeting m WHERE m.tenantSegment = :tenant " +
	       "AND m.meetingDate >= :date " +
	       "AND (m.participants LIKE %:username% OR m.scheduledBy = :username) " +
	       "ORDER BY m.meetingDate ASC, m.meetingTime ASC")
	List<Meeting> findUpcomingMeetingsForUserOrHost(
			@Param("tenant") String tenant,
			@Param("username") String username,
			@Param("date") LocalDate date);
}