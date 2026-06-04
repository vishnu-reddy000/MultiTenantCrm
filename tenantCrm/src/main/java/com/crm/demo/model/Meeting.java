package com.crm.demo.model;

import java.time.LocalDate;
import java.time.LocalTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;

@Entity
@Table(name = "meetings")
public class Meeting {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@NotBlank
	private String title;

	private LocalDate meetingDate;
	private LocalTime meetingTime;
	private Integer duration;
	private String meetingType;
	private String location;

	@NotBlank
	private String participants;

	@Column(length = 2000)
	private String agenda;

	private boolean sendNotification;

	private String tenantSegment;

	/** Username of the person who scheduled this meeting. */
	@Column(name = "created_by")
	private String scheduledBy;

	// Getters & Setters
	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public LocalDate getMeetingDate() {
		return meetingDate;
	}

	public void setMeetingDate(LocalDate d) {
		this.meetingDate = d;
	}

	public LocalTime getMeetingTime() {
		return meetingTime;
	}

	public void setMeetingTime(LocalTime t) {
		this.meetingTime = t;
	}

	public Integer getDuration() {
		return duration;
	}

	public void setDuration(Integer duration) {
		this.duration = duration;
	}

	public String getMeetingType() {
		return meetingType;
	}

	public void setMeetingType(String type) {
		this.meetingType = type;
	}

	public String getLocation() {
		return location;
	}

	public void setLocation(String location) {
		this.location = location;
	}

	public String getParticipants() {
		return participants;
	}

	public void setParticipants(String p) {
		this.participants = p;
	}

	public String getAgenda() {
		return agenda;
	}

	public void setAgenda(String agenda) {
		this.agenda = agenda;
	}

	public boolean isSendNotification() {
		return sendNotification;
	}

	public void setSendNotification(boolean b) {
		this.sendNotification = b;
	}

	public String getTenantSegment() {
		return tenantSegment;
	}

	public void setTenantSegment(String tenantSegment) {
		this.tenantSegment = tenantSegment;
	}

	public String getScheduledBy() {
		return scheduledBy;
	}

	public void setScheduledBy(String scheduledBy) {
		this.scheduledBy = scheduledBy;
	}
}