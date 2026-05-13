package com.springboot1.controller;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "leads")
public class Lead {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "customer_name", nullable = false, length = 150)
	private String customerName;

	@Column(length = 150)
	private String email;

	@Column(length = 20)
	private String phone;

	@Column(length = 150)
	private String company;

	@Enumerated(EnumType.STRING)
	private Source source = Source.OTHER;

	@Column(name = "deal_value", precision = 15, scale = 2)
	private BigDecimal dealValue = BigDecimal.ZERO;

	@Column(columnDefinition = "TEXT")
	private String notes;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private LeadStatus status = LeadStatus.PENDING;

	@Column(name = "rejection_note", length = 500)
	private String rejectionNote;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "created_by")
	private Employee createdBy;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "assigned_to")
	private Employee assignedTo;

	// Stores the username of the assigned sales executive directly for easy DB visibility
	@Column(name = "assigned_to_username", length = 100)
	private String assignedToUsername;

	// Direct tenant reference — set when createdBy employee is not available
	@Column(name = "tenant_id", length = 30)
	private String tenantId;

	@Column(name = "approved_at")
	private LocalDateTime approvedAt;

	@Column(name = "created_at", updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at")
	private LocalDateTime updatedAt;

	@PrePersist
	protected void onCreate() {
		createdAt = LocalDateTime.now();
		updatedAt = LocalDateTime.now();
	}

	@PreUpdate
	protected void onUpdate() {
		updatedAt = LocalDateTime.now();
	}

	// ── Enums ──────────────────────────────────────
	public enum Source {
		WEBSITE, REFERRAL, SOCIAL, COLD_CALL, EMAIL, OTHER
	}

	public enum LeadStatus {
		PENDING, APPROVED, REJECTED, CONTACTED, QUALIFIED, WON, LOST;

		public String getDisplayName() {
			return name().charAt(0) + name().substring(1).toLowerCase();
		}
	}

	// ── Constructors ───────────────────────────────
	public Lead() {
	}

	// ── Getters & Setters ──────────────────────────
	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getCustomerName() {
		return customerName;
	}

	public void setCustomerName(String customerName) {
		this.customerName = customerName;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public String getPhone() {
		return phone;
	}

	public void setPhone(String phone) {
		this.phone = phone;
	}

	public String getCompany() {
		return company;
	}

	public void setCompany(String company) {
		this.company = company;
	}

	public Source getSource() {
		return source;
	}

	public void setSource(Source source) {
		this.source = source;
	}

	public BigDecimal getDealValue() {
		return dealValue;
	}

	public void setDealValue(BigDecimal dealValue) {
		this.dealValue = dealValue;
	}

	public String getNotes() {
		return notes;
	}

	public void setNotes(String notes) {
		this.notes = notes;
	}

	public LeadStatus getStatus() {
		return status;
	}

	public void setStatus(LeadStatus status) {
		this.status = status;
	}

	public String getRejectionNote() {
		return rejectionNote;
	}

	public void setRejectionNote(String note) {
		this.rejectionNote = note;
	}

	public Employee getCreatedBy() {
		return createdBy;
	}

	public void setCreatedBy(Employee createdBy) {
		this.createdBy = createdBy;
	}

	public Employee getAssignedTo() {
		return assignedTo;
	}

	public void setAssignedTo(Employee assignedTo) {
		this.assignedTo = assignedTo;
	}

	public String getAssignedToUsername() {
		return assignedToUsername;
	}

	public void setAssignedToUsername(String assignedToUsername) {
		this.assignedToUsername = assignedToUsername;
	}

	public LocalDateTime getApprovedAt() {
		return approvedAt;
	}

	public void setApprovedAt(LocalDateTime t) {
		this.approvedAt = t;
	}

	public String getTenantId() {
		return tenantId;
	}

	public void setTenantId(String tenantId) {
		this.tenantId = tenantId;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}
}