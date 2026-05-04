package com.springboot1.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload sent by the Super Admin when creating or updating a tenant. Maps
 * 1-to-1 with the HTML modal form fields.
 */
public class TenantRequest {

	@NotBlank(message = "Company name is required")
	@Size(max = 150, message = "Company name must be under 150 characters")
	private String companyName;

	@NotBlank(message = "Phone number is required")
	@Size(max = 30, message = "Phone must be under 30 characters")
	private String phone;

	@NotBlank(message = "Address is required")
	@Size(max = 255, message = "Address must be under 255 characters")
	private String address;

	@NotBlank(message = "Email is required")
	@Email(message = "Must be a valid email address")
	@Size(max = 100)
	private String email;

	// On CREATE: required (min 8 chars).
	// On UPDATE: optional — send empty string to keep existing password.
	@Size(min = 0, max = 100)
	private String password;

	// ACTIVE | PENDING | INACTIVE
	private String status = "ACTIVE";

	// ── Getters & Setters ─────────────────────────────────────────────────

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

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}
}