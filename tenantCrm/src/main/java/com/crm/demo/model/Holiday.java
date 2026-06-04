package com.crm.demo.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Data
@Table(name = "holiday")
public class Holiday {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String date;          // "YYYY-MM-DD"

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String type;          // "public" | "optional" | "company"

    /** Tenant segment (e.g. "tcs") — isolates holidays per company. */
    @Column(nullable = false)
    private String tenantSegment;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getDate() {
		return date;
	}

	public void setDate(String date) {
		this.date = date;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public String getTenantSegment() {
		return tenantSegment;
	}

	public void setTenantSegment(String tenantSegment) {
		this.tenantSegment = tenantSegment;
	}
}
