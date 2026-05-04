package com.springboot1.service;

import java.time.LocalDateTime;
import java.time.Year;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.springboot1.dto.TenantRequest;
import com.springboot1.dto.TenantResponse;
import com.springboot1.model.Role;
import com.springboot1.model.TenantStatus;
import com.springboot1.model.User;
import com.springboot1.repository.UserRepository;

@Service
public class TenantService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final Random random = new Random();

	public TenantService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
	}

	// ── 1. Generate unique tenant ID ─────────────────────────────────────
	/**
	 * Generates IDs in the format TNT-YYYY-XXXX (e.g. TNT-2026-4821). Retries up to
	 * 10 times to guarantee uniqueness in the DB.
	 */
	public String generateUniqueTenantId() {
		int year = Year.now().getValue();
		for (int i = 0; i < 10; i++) {
			String id = "TNT-" + year + "-" + String.format("%04d", random.nextInt(9000) + 1000);
			if (!userRepository.existsByTenantId(id)) {
				return id;
			}
		}
		// Fallback: append timestamp millis for absolute uniqueness
		return "TNT-" + year + "-" + (System.currentTimeMillis() % 100000);
	}

	// ── 2. Create tenant ──────────────────────────────────────────────────
	/**
	 * Creates the tenant root account in the `user` table. Role is set to ADMIN —
	 * the tenant's own super user. All future users created by this tenant will
	 * share the same tenantId.
	 *
	 * @throws IllegalArgumentException if email/username already exists
	 */
	@Transactional
	public TenantResponse createTenant(TenantRequest req) {

		// Email uniqueness check
		if (userRepository.existsByEmail(req.getEmail())) {
			throw new IllegalArgumentException("Email already registered: " + req.getEmail());
		}

		// Derive username from email local-part (email prefix before @)
		String baseUsername = req.getEmail().split("@")[0];
		String username = uniqueUsername(baseUsername);

		// Password must be supplied on create
		if (req.getPassword() == null || req.getPassword().trim().length() < 8) {
			throw new IllegalArgumentException("Password must be at least 8 characters");
		}

		User user = new User();
		user.setTenantId(generateUniqueTenantId());
		user.setUsername(username);
		user.setEmail(req.getEmail());
		user.setPassword(passwordEncoder.encode(req.getPassword()));
		user.setRole(Role.ADMIN); // tenant root = ADMIN
		user.setCompanyName(req.getCompanyName());
		user.setPhone(req.getPhone());
		user.setAddress(req.getAddress());
		user.setStatus(parseTenantStatus(req.getStatus()));
		user.setCreatedAt(LocalDateTime.now());

		return TenantResponse.from(userRepository.save(user));
	}

	// ── 3. Get all tenants ────────────────────────────────────────────────
	public List<TenantResponse> getAllTenants() {
		return userRepository.findByRoleAndTenantIdIsNotNull(Role.ADMIN).stream().map(TenantResponse::from)
				.collect(Collectors.toList());
	}

	// ── 4. Get single tenant by DB id ────────────────────────────────────
	public TenantResponse getTenantById(Long id) {
		User user = findTenantUserById(id);
		return TenantResponse.from(user);
	}

	// ── 5. Update tenant ─────────────────────────────────────────────────
	@Transactional
	public TenantResponse updateTenant(Long id, TenantRequest req) {
		User user = findTenantUserById(id);

		// If email changed, check it is still unique
		if (!user.getEmail().equalsIgnoreCase(req.getEmail()) && userRepository.existsByEmail(req.getEmail())) {
			throw new IllegalArgumentException("Email already registered: " + req.getEmail());
		}

		user.setEmail(req.getEmail());
		user.setCompanyName(req.getCompanyName());
		user.setPhone(req.getPhone());
		user.setAddress(req.getAddress());
		user.setStatus(parseTenantStatus(req.getStatus()));

		// Only update password if a new one was supplied
		if (req.getPassword() != null && !req.getPassword().isBlank()) {
			if (req.getPassword().length() < 8) {
				throw new IllegalArgumentException("Password must be at least 8 characters");
			}
			user.setPassword(passwordEncoder.encode(req.getPassword()));
		}

		return TenantResponse.from(userRepository.save(user));
	}

	// ── 6. Delete tenant ─────────────────────────────────────────────────
	@Transactional
	public void deleteTenant(Long id) {
		User user = findTenantUserById(id);
		userRepository.delete(user);
	}

	// ── 7. Stats for KPI cards ────────────────────────────────────────────
	public Map<String, Long> getStats() {
		List<User> tenants = userRepository.findByRoleAndTenantIdIsNotNull(Role.ADMIN);
		long total = tenants.size();
		long active = tenants.stream().filter(u -> u.getStatus() == TenantStatus.ACTIVE).count();
		long pending = tenants.stream().filter(u -> u.getStatus() == TenantStatus.PENDING).count();
		long inactive = tenants.stream().filter(u -> u.getStatus() == TenantStatus.INACTIVE).count();
		return Map.of("total", total, "active", active, "pending", pending, "inactive", inactive);
	}

	// ─────────────────────────────────────────────────────────────────────
	// Private helpers
	// ─────────────────────────────────────────────────────────────────────

	private User findTenantUserById(Long id) {
		return userRepository.findById(id).filter(u -> u.getTenantId() != null && u.getRole() == Role.ADMIN)
				.orElseThrow(() -> new IllegalArgumentException("Tenant not found with id: " + id));
	}

	/** Ensure the derived username is unique by appending a counter if needed. */
	private String uniqueUsername(String base) {
		if (!userRepository.existsByUsername(base))
			return base;
		int counter = 1;
		while (userRepository.existsByUsername(base + counter))
			counter++;
		return base + counter;
	}

	private TenantStatus parseTenantStatus(String status) {
		try {
			return TenantStatus.valueOf(status.toUpperCase());
		} catch (Exception e) {
			return TenantStatus.ACTIVE;
		}
	}
}