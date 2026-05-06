package com.springboot1.service;

import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.springboot1.controller.Employee;
import com.springboot1.model.Role;
import com.springboot1.model.TenantStatus;
import com.springboot1.model.User;
import com.springboot1.repository.EmployeeRepository;
import com.springboot1.repository.UserRepository;

@Service
@Transactional
public class UserService {

	private final UserRepository userRepo;
	private final EmployeeRepository empRepo;
	private final PasswordEncoder passwordEncoder;

	public UserService(UserRepository userRepo, EmployeeRepository empRepo, PasswordEncoder passwordEncoder) {
		this.userRepo = userRepo;
		this.empRepo = empRepo;
		this.passwordEncoder = passwordEncoder;
	}

	// ── List all users with MANAGER or SALES_EXECUTIVE role ──────────────────
	public List<User> getAllStaffUsers() {
		List<User> managers = userRepo.findByRoleAndStatus(Role.MANAGER, TenantStatus.ACTIVE);
		List<User> salesExecs = userRepo.findByRoleAndStatus(Role.SALES_EXECUTIVE, TenantStatus.ACTIVE);
		managers.addAll(salesExecs);
		return managers;
	}

	public List<User> getUsersByRole(Role role) {
		return userRepo.findByRoleAndStatus(role, TenantStatus.ACTIVE);
	}

	// ── Create a new MANAGER or SALES_EXECUTIVE user ──────────────────────────
	// This creates:
	//   1. A User record (for login)
	//   2. An Employee record (for CRM operations)
	public User createStaffUser(String fullName, String username, String email, String password,
			String phone, String department, Role role) {

		// Validate uniqueness
		if (userRepo.existsByUsername(username)) {
			throw new IllegalArgumentException("Username '" + username + "' is already taken.");
		}
		if (userRepo.existsByEmail(email)) {
			throw new IllegalArgumentException("Email '" + email + "' is already registered.");
		}
		if (empRepo.existsByEmail(email)) {
			throw new IllegalArgumentException("An employee with email '" + email + "' already exists.");
		}

		// Only allow MANAGER or SALES_EXECUTIVE to be created by admin
		if (role != Role.MANAGER && role != Role.SALES_EXECUTIVE) {
			throw new IllegalArgumentException("Admin can only create MANAGER or SALES_EXECUTIVE users.");
		}

		// 1. Create User record
		User user = new User();
		user.setUsername(username);
		user.setEmail(email);
		user.setPassword(passwordEncoder.encode(password));
		user.setRole(role);
		user.setPhone(phone);
		user.setStatus(TenantStatus.ACTIVE);
		User savedUser = userRepo.save(user);

		// 2. Create Employee record linked to the user
		Employee emp = new Employee();
		emp.setName(fullName);
		emp.setEmail(email);
		emp.setPhone(phone);
		emp.setDepartment(department != null && !department.isBlank() ? department : "Sales");
		emp.setRole(role == Role.MANAGER ? Employee.Role.MANAGER : Employee.Role.SALES_EXECUTIVE);
		emp.setStatus(Employee.Status.ACTIVE);
		emp.setUserId(savedUser.getId());
		empRepo.save(emp);

		return savedUser;
	}
}
