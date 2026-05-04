package com.springboot1.dto;

import java.time.format.DateTimeFormatter;

import com.springboot1.model.User;

/**
 * Safe read-only view of a tenant — password is never included. Sent as JSON to
 * the frontend table / view modal.
 */
public class TenantResponse {

	private Long id;
	private String tenantId;
	private String companyName;
	private String email;
	private String phone;
	private String address;
	private String status;
	private String createdAt;

	// ── Factory ───────────────────────────────────────────────────────────
	public static TenantResponse from(User user) {
		TenantResponse r = new TenantResponse();
		r.id = user.getId();
		r.tenantId = user.getTenantId();
		r.companyName = user.getCompanyName();
		r.email = user.getEmail();
		r.phone = user.getPhone();
		r.address = user.getAddress();
		r.status = user.getStatus() != null ? user.getStatus().name() : "ACTIVE";
		r.createdAt = user.getCreatedAt() != null
				? user.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
				: "";
		return r;
	}

	// ── Getters (Jackson needs them) ──────────────────────────────────────
	public Long getId() {
		return id;
	}

	public String getTenantId() {
		return tenantId;
	}

	public String getCompanyName() {
		return companyName;
	}

	public String getEmail() {
		return email;
	}

	public String getPhone() {
		return phone;
	}

	public String getAddress() {
		return address;
	}

	public String getStatus() {
		return status;
	}

	public String getCreatedAt() {
		return createdAt;
	}
}