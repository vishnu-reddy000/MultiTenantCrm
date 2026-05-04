package com.springboot1.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DashboardController {

	@GetMapping("/dashboard/super-admin")
	public String superAdminDashboard() {
		return "dashboard-super-admin"; // → templates/dashboard-super-admin.html
	}

	@GetMapping("/dashboard/admin")
	public String adminDashboard() {
		return "dashboard-admin";
	}

	@GetMapping("/dashboard/user")
	public String userDashboard() {
		return "dashboard-user";
	}

	@GetMapping("/dashboard/sales-executive")
	public String salesExecutiveDashboard() {
		return "dashboard-sales-executive";
	}

	// ── Dedicated Tenant Management page (Super Admin only) ──────────────
	// Security enforced in SecurityConfig — only SUPER_ADMIN can reach this.
	@GetMapping("/super-admin/tenants/page")
	public String tenantManagementPage() {
		return "tenant-management"; // → templates/tenant-management.html
	}
}