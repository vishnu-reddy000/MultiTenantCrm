package com.springboot1.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.springboot1.model.Role;
import com.springboot1.service.EmployeeService;
import com.springboot1.service.LeadService;
import com.springboot1.service.UserService;

@Controller
@RequestMapping("/admin")
public class AdminController {

	private final EmployeeService empService;
	private final LeadService leadService;
	private final UserService userService;

	public AdminController(EmployeeService empService, LeadService leadService, UserService userService) {
		this.empService = empService;
		this.leadService = leadService;
		this.userService = userService;
	}

	// ════════════════════════════════════════════════════════════════════════
	// DASHBOARD
	// ════════════════════════════════════════════════════════════════════════

	@GetMapping("/dashboard")
	public String dashboard(Model model) {
		model.addAttribute("totalEmployees", empService.countTotal());
		model.addAttribute("totalManagers", empService.countManagers());
		model.addAttribute("totalSalesExecs", empService.countSalesExecutives());
		model.addAttribute("activeEmployees", empService.countActive());
		model.addAttribute("pendingLeads", leadService.countPending());
		model.addAttribute("approvedLeads", leadService.countApproved());
		model.addAttribute("totalLeads", leadService.countTotal());
		model.addAttribute("approvedValue", leadService.sumApprovedValue());
		model.addAttribute("recentLeads", leadService.getPendingLeads());
		return "dashboard-admin";
	}

	// ════════════════════════════════════════════════════════════════════════
	// EMPLOYEES — List
	// ════════════════════════════════════════════════════════════════════════

	@GetMapping("/employees")
	public String listEmployees(Model model, @RequestParam(required = false) String role,
			@RequestParam(required = false) String success, @RequestParam(required = false) String error) {
		if ("MANAGER".equals(role)) {
			model.addAttribute("employees", empService.getManagers());
			model.addAttribute("filterRole", "MANAGER");
		} else if ("SALES_EXECUTIVE".equals(role)) {
			model.addAttribute("employees", empService.getSalesExecutives());
			model.addAttribute("filterRole", "SALES_EXECUTIVE");
		} else {
			model.addAttribute("employees", empService.getAllEmployees());
			model.addAttribute("filterRole", "ALL");
		}

		model.addAttribute("newEmployee", new Employee());
		model.addAttribute("totalManagers", empService.countManagers());
		model.addAttribute("totalSalesExecs", empService.countSalesExecutives());
		model.addAttribute("totalActive", empService.countActive());

		if (success != null)
			model.addAttribute("success", success);
		if (error != null)
			model.addAttribute("error", error);

		return "admin-employees";
	}

	// ════════════════════════════════════════════════════════════════════════
	// EMPLOYEES — Add
	// ════════════════════════════════════════════════════════════════════════

	@PostMapping("/employees/add")
	public String addEmployee(@ModelAttribute Employee employee, RedirectAttributes ra) {
		try {
			empService.addEmployee(employee);
			ra.addFlashAttribute("success", "Employee '" + employee.getName() + "' added successfully.");
		} catch (IllegalArgumentException e) {
			ra.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/admin/employees";
	}

	// ════════════════════════════════════════════════════════════════════════
	// EMPLOYEES — Edit (load into modal via AJAX)
	// ════════════════════════════════════════════════════════════════════════

	@GetMapping("/employees/{id}/json")
	@ResponseBody
	public Employee getEmployeeJson(@PathVariable Long id) {
		return empService.getById(id).orElseThrow(() -> new IllegalArgumentException("Not found: " + id));
	}

	// ════════════════════════════════════════════════════════════════════════
	// EMPLOYEES — Update
	// ════════════════════════════════════════════════════════════════════════

	@PostMapping("/employees/{id}/update")
	public String updateEmployee(@PathVariable Long id, @ModelAttribute Employee employee, RedirectAttributes ra) {
		try {
			empService.updateEmployee(id, employee);
			ra.addFlashAttribute("success", "Employee updated successfully.");
		} catch (IllegalArgumentException e) {
			ra.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/admin/employees";
	}

	// ════════════════════════════════════════════════════════════════════════
	// EMPLOYEES — Toggle Status
	// ════════════════════════════════════════════════════════════════════════

	@PostMapping("/employees/{id}/toggle-status")
	public String toggleStatus(@PathVariable Long id, RedirectAttributes ra) {
		try {
			Employee emp = empService.toggleStatus(id);
			ra.addFlashAttribute("success", emp.getName() + " is now " + emp.getStatus().name().toLowerCase() + ".");
		} catch (IllegalArgumentException e) {
			ra.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/admin/employees";
	}

	// ════════════════════════════════════════════════════════════════════════
	// EMPLOYEES — Delete
	// ════════════════════════════════════════════════════════════════════════

	@PostMapping("/employees/{id}/delete")
	public String deleteEmployee(@PathVariable Long id, RedirectAttributes ra) {
		try {
			empService.deleteEmployee(id);
			ra.addFlashAttribute("success", "Employee deleted successfully.");
		} catch (Exception e) {
			ra.addFlashAttribute("error", "Cannot delete employee: " + e.getMessage());
		}
		return "redirect:/admin/employees";
	}

	// ════════════════════════════════════════════════════════════════════════
	// LEADS — List / Approval Queue
	// ════════════════════════════════════════════════════════════════════════

	@GetMapping("/leads")
	public String listLeads(Model model, @RequestParam(required = false) String status) {
		if ("PENDING".equals(status)) {
			model.addAttribute("leads", leadService.getPendingLeads());
			model.addAttribute("filter", "PENDING");
		} else {
			model.addAttribute("leads", leadService.getAllLeads());
			model.addAttribute("filter", "ALL");
		}

		model.addAttribute("salesExecs", empService.getActiveSalesExecutives());
		model.addAttribute("pendingCount", leadService.countPending());
		model.addAttribute("approvedCount", leadService.countApproved());
		model.addAttribute("rejectedCount", leadService.countRejected());
		model.addAttribute("totalLeads", leadService.countTotal());

		return "admin-lead-approvals";
	}

	// ════════════════════════════════════════════════════════════════════════
	// LEADS — Approve
	// ════════════════════════════════════════════════════════════════════════

	@PostMapping("/leads/{id}/approve")
	public String approveLead(@PathVariable Long id, @RequestParam(required = false) Long assignTo,
			RedirectAttributes ra) {
		try {
			Lead lead = leadService.approveLead(id, assignTo);
			ra.addFlashAttribute("success", "Lead for '" + lead.getCustomerName() + "' approved successfully.");
		} catch (Exception e) {
			ra.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/admin/leads";
	}

	// ════════════════════════════════════════════════════════════════════════
	// LEADS — Reject
	// ════════════════════════════════════════════════════════════════════════

	@PostMapping("/leads/{id}/reject")
	public String rejectLead(@PathVariable Long id, @RequestParam(required = false) String rejectionNote,
			RedirectAttributes ra) {
		try {
			Lead lead = leadService.rejectLead(id, rejectionNote);
			ra.addFlashAttribute("success", "Lead for '" + lead.getCustomerName() + "' rejected.");
		} catch (Exception e) {
			ra.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/admin/leads";
	}

	// ════════════════════════════════════════════════════════════════════════
	// LEADS — Reset to Pending
	// ════════════════════════════════════════════════════════════════════════

	@PostMapping("/leads/{id}/reset")
	public String resetLead(@PathVariable Long id, RedirectAttributes ra) {
		try {
			leadService.resetToPending(id);
			ra.addFlashAttribute("success", "Lead reset to pending for re-review.");
		} catch (Exception e) {
			ra.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/admin/leads";
	}

	// ════════════════════════════════════════════════════════════════════════
	// LEADS — Delete
	// ════════════════════════════════════════════════════════════════════════

	@PostMapping("/leads/{id}/delete")
	public String deleteLead(@PathVariable Long id, RedirectAttributes ra) {
		try {
			leadService.deleteLead(id);
			ra.addFlashAttribute("success", "Lead deleted.");
		} catch (Exception e) {
			ra.addFlashAttribute("error", "Cannot delete lead: " + e.getMessage());
		}
		return "redirect:/admin/leads";
	}

	// ════════════════════════════════════════════════════════════════════════
	// USERS — Add User page
	// ════════════════════════════════════════════════════════════════════════

	@GetMapping("/users/add")
	public String showAddUserPage(Model model) {
		model.addAttribute("roles", new String[]{"MANAGER", "SALES_EXECUTIVE"});
		return "admin-add-user";
	}

	@PostMapping("/users/add")
	public String createUser(
			@RequestParam String fullName,
			@RequestParam String username,
			@RequestParam String email,
			@RequestParam String password,
			@RequestParam String phone,
			@RequestParam(required = false) String department,
			@RequestParam String role,
			RedirectAttributes ra) {
		try {
			Role userRole = Role.valueOf(role);
			userService.createStaffUser(fullName, username, email, password, phone, department, userRole);
			ra.addFlashAttribute("success",
					userRole == Role.MANAGER
							? "Manager '" + fullName + "' created successfully."
							: "Sales Executive '" + fullName + "' created successfully.");
			return "redirect:/admin/users/add";
		} catch (IllegalArgumentException e) {
			ra.addFlashAttribute("error", e.getMessage());
			ra.addFlashAttribute("formFullName", fullName);
			ra.addFlashAttribute("formUsername", username);
			ra.addFlashAttribute("formEmail", email);
			ra.addFlashAttribute("formPhone", phone);
			ra.addFlashAttribute("formDepartment", department);
			ra.addFlashAttribute("formRole", role);
			return "redirect:/admin/users/add";
		}
	}

	// ════════════════════════════════════════════════════════════════════════
	// USERS — List Users
	// ════════════════════════════════════════════════════════════════════════

	@GetMapping("/users")
	public String listUsers(Model model) {
		model.addAttribute("managers", userService.getUsersByRole(Role.MANAGER));
		model.addAttribute("salesExecs", userService.getUsersByRole(Role.SALES_EXECUTIVE));
		return "admin-users";
	}
}