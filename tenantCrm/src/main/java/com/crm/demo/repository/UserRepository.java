package com.crm.demo.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.crm.demo.model.User;

public interface UserRepository extends JpaRepository<User, Long> {

	// =========================
	// LOGIN METHODS
	// =========================

	User findByUsername(String username);

	User findByEmail(String email);

	User findByUsernameOrEmail(String username, String email);

	// =========================
	// TENANT FILTER
	// Example:
	// tcs -> emp.tcs@crm.com
	// =========================

	@Query("SELECT u FROM User u WHERE u.email LIKE %:tenantSegment%")
	List<User> findByTenantSegment(@Param("tenantSegment") String tenantSegment);

	// =========================
	// ONLY EMPLOYEES BY TENANT
	// =========================

	@Query("SELECT u FROM User u WHERE u.email LIKE %:tenantSegment% AND UPPER(u.role) = 'EMPLOYEE'")
	List<User> findEmployeesByTenant(@Param("tenantSegment") String tenantSegment);
}