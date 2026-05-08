package com.springboot1.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DashboardController {

	@GetMapping("/dashboard/super-admin")
	public String superAdminDashboard() {
		return "redirect:/super-admin/dashboard";
	}

	@GetMapping("/dashboard/admin")
	public String adminDashboard() {
		return "dashboard-admin";
	}

	@GetMapping("/dashboard/user")
	public String userDashboard() {
		return "redirect:/user/home";
	}

	@GetMapping("/dashboard/manager")
	public String managerDashboard() {
		return "dashboard-manager"; // → templates/dashboard-manager.html
	}

	@GetMapping("/dashboard/sales-executive")
	public String salesExecutiveDashboard() {
		return "redirect:/sales/dashboard";
	}

	// ── Dedicated Tenant Management page (Super Admin only) ──────────────
	// Security enforced in SecurityConfig — only SUPER_ADMIN can reach this.
	@GetMapping("/super-admin/tenants/page")
	public String tenantManagementPage() {
		return "tenant-management"; // → templates/tenant-management.html
	}
}