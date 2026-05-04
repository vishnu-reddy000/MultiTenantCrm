package com.springboot1.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "`user`")
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	// ── Unique tenant identifier e.g. TNT-2026-4821 ──────────────────────
	// Null for SUPER_ADMIN accounts; every ADMIN/USER/SALES_EXECUTIVE
	// created inside a tenant org carries the same tenantId so you can
	// filter with: userRepository.findByTenantId(tenantId)
	@Column(name = "tenant_id", length = 30)
	private String tenantId;

	@Column(nullable = false, unique = true, length = 50)
	private String username;

	@Column(nullable = false, unique = true, length = 100)
	private String email;

	@Column(nullable = false)
	private String password;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Role role;

	// ── Tenant company details (populated when role = ADMIN = tenant root) ──
	@Column(name = "company_name", length = 150)
	private String companyName;

	@Column(name = "phone", length = 30)
	private String phone;

	@Column(name = "address", length = 255)
	private String address;

	// ── Tenant account status ─────────────────────────────────────────────
	@Enumerated(EnumType.STRING)
	@Column(name = "status", length = 20)
	private TenantStatus status = TenantStatus.ACTIVE;

	@Column(name = "created_at", updatable = false)
	private LocalDateTime createdAt = LocalDateTime.now();

	// ─────────────────────────────────────────────────────────────────────
	// Getters & Setters
	// ─────────────────────────────────────────────────────────────────────

	public Long getId() {
		return id;
	}

	public String getTenantId() {
		return tenantId;
	}

	public void setTenantId(String tenantId) {
		this.tenantId = tenantId;
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public Role getRole() {
		return role;
	}

	public void setRole(Role role) {
		this.role = role;
	}

	public String getCompanyName() {
		return companyName;
	}

	public void setCompanyName(String companyName) {
		this.companyName = companyName;
	}

	public String getPhone() {
		return phone;
	}

	public void setPhone(String phone) {
		this.phone = phone;
	}

	public String getAddress() {
		return address;
	}

	public void setAddress(String address) {
		this.address = address;
	}

	public TenantStatus getStatus() {
		return status;
	}

	public void setStatus(TenantStatus status) {
		this.status = status;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}
}