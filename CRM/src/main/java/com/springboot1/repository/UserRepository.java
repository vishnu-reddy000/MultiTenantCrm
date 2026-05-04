package com.springboot1.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.springboot1.model.Role;
import com.springboot1.model.TenantStatus;
import com.springboot1.model.User;

public interface UserRepository extends JpaRepository<User, Long> {

	// ── Existing queries (keep as-is) ────────────────────────────────────
	Optional<User> findByUsernameOrEmail(String username, String email);

	Optional<User> findByUsername(String username);

	// ── Tenant queries ────────────────────────────────────────────────────
	/** All users that belong to a given tenant (for data isolation). */
	List<User> findByTenantId(String tenantId);

	/** The root admin account of a tenant (role = ADMIN + tenantId). */
	Optional<User> findByTenantIdAndRole(String tenantId, Role role);

	/**
	 * All tenant root accounts (every row where role = ADMIN and tenantId != null).
	 */
	List<User> findByRoleAndTenantIdIsNotNull(Role role);

	/** Filter tenant list by status. */
	List<User> findByRoleAndStatus(Role role, TenantStatus status);

	/** Check duplicate email before creating. */
	boolean existsByEmail(String email);

	/** Check duplicate username before creating. */
	boolean existsByUsername(String username);

	/** Check duplicate tenantId (safety guard). */
	boolean existsByTenantId(String tenantId);
}