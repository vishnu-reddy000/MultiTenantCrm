package com.springboot1.controller;

import java.math.BigDecimal;
import java.security.Principal;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.springboot1.model.User;
import com.springboot1.repository.EmployeeRepository;
import com.springboot1.repository.UserRepository;
import com.springboot1.service.EmployeeService;
import com.springboot1.service.LeadService;

@Controller
@RequestMapping("/manager")
public class ManagerController {

	private final EmployeeService empService;
	private final LeadService leadService;
	private final UserRepository userRepository;
	private final EmployeeRepository employeeRepository;

	public ManagerController(EmployeeService empService, LeadService leadService,
			UserRepository userRepository, EmployeeRepository employeeRepository) {
		this.empService = empService;
		this.leadService = leadService;
		this.userRepository = userRepository;
		this.employeeRepository = employeeRepository;
	}

	// ── Helper: resolve tenantId from logged-in user ──────────────────────
	private String tid(Principal principal) {
		if (principal == null) return null;
		return userRepository.findByUsername(principal.getName())
				.map(User::getTenantId).orElse(null);
	}

	// ── Inject managerUser into every manager page automatically ──────────
	@ModelAttribute
	public void addManagerUser(Model model, Principal principal) {
		if (principal != null) {
			userRepository.findByUsername(principal.getName())
					.ifPresent(u -> model.addAttribute("managerUser", u));
		}
	}

	// ════════════════════════════════════════════════════════════════════════
	// DASHBOARD
	// ════════════════════════════════════════════════════════════════════════

	@GetMapping("/dashboard")
	public String dashboard(Model model, Principal principal) {
		String t = tid(principal);
		model.addAttribute("totalLeads",    leadService.countTotalByTenant(t));
		model.addAttribute("approvedLeads", leadService.countApprovedByTenant(t));
		model.addAttribute("pendingLeads",  leadService.countPendingByTenant(t));
		model.addAttribute("rejectedLeads", leadService.countRejectedByTenant(t));
		model.addAttribute("approvedValue", leadService.sumApprovedValueByTenant(t));
		model.addAttribute("recentLeads",   leadService.getAllLeadsByTenant(t));
		model.addAttribute("salesExecs",    empService.getActiveSalesExecutivesByTenant(t));
		return "dashboard-manager";
	}

	// ════════════════════════════════════════════════════════════════════════
	// TEAM OVERVIEW
	// ════════════════════════════════════════════════════════════════════════

	@GetMapping("/team/overview")
	public String teamOverview(Model model, Principal principal) {
		String t = tid(principal);
		model.addAttribute("totalManagers",   empService.countManagersByTenant(t));
		model.addAttribute("totalSalesExecs", empService.countSalesExecutivesByTenant(t));
		model.addAttribute("activeEmployees", empService.countActiveByTenant(t));
		return "manager-team-overview";
	}

	@GetMapping("/team/sales")
	public String salesTeam(Model model, Principal principal) {
		model.addAttribute("salesExecs", empService.getSalesExecutivesByTenant(tid(principal)));
		return "manager-sales-team";
	}

	// ════════════════════════════════════════════════════════════════════════
	// LEAD MANAGEMENT
	// ════════════════════════════════════════════════════════════════════════

	@GetMapping("/leads/assigned")
	public String assignedLeads(Model model, Principal principal) {
		String t = tid(principal);
		model.addAttribute("leads",          leadService.getAllLeadsByTenant(t));
		model.addAttribute("totalLeads",     leadService.countTotalByTenant(t));
		model.addAttribute("pendingCount",   leadService.countPendingByTenant(t));
		model.addAttribute("approvedCount",  leadService.countApprovedByTenant(t));
		model.addAttribute("rejectedCount",  leadService.countRejectedByTenant(t));
		model.addAttribute("salesExecs",     empService.getActiveSalesExecutivesByTenant(t));
		return "manager-assigned-leads";
	}

	@GetMapping("/leads/tracking")
	public String leadTracking(Model model, Principal principal) {
		String t = tid(principal);
		model.addAttribute("leads",         leadService.getAllLeadsByTenant(t));
		model.addAttribute("totalLeads",    leadService.countTotalByTenant(t));
		model.addAttribute("approvedLeads", leadService.countApprovedByTenant(t));
		model.addAttribute("pendingLeads",  leadService.countPendingByTenant(t));
		return "manager-lead-tracking";
	}

	// ── Submit a new lead (stored as PENDING, admin must approve) ─────────
	@PostMapping("/leads/submit")
	public String submitLead(
			@RequestParam String customerName,
			@RequestParam(required = false) String company,
			@RequestParam(required = false) String email,
			@RequestParam(required = false) String phone,
			@RequestParam(required = false, defaultValue = "0") BigDecimal dealValue,
			@RequestParam(required = false, defaultValue = "OTHER") String source,
			@RequestParam(required = false) String notes,
			@RequestParam(required = false) Long assignToId,
			Principal principal,
			RedirectAttributes ra) {
		try {
			String t = tid(principal);

			// Try to resolve the manager's Employee record (createdBy)
			Employee createdBy = userRepository.findByUsername(principal.getName())
					.flatMap(u -> employeeRepository.findByUserId(u.getId()))
					.orElse(null);

			// If no Employee record, try to find any manager employee in this tenant
			if (createdBy == null && t != null) {
				createdBy = empService.getManagersByTenant(t).stream().findFirst().orElse(null);
			}

			Lead lead = new Lead();
			lead.setCustomerName(customerName);
			lead.setCompany(company);
			lead.setEmail(email);
			lead.setPhone(phone);
			lead.setDealValue(dealValue != null ? dealValue : BigDecimal.ZERO);
			try { lead.setSource(Lead.Source.valueOf(source)); } catch (Exception e) { lead.setSource(Lead.Source.OTHER); }
			lead.setNotes(notes);
			lead.setStatus(Lead.LeadStatus.PENDING);
			lead.setTenantId(t); // always set direct tenantId for reliable scoping
			if (createdBy != null) {
				lead.setCreatedBy(createdBy);
			}

			if (assignToId != null) {
				empService.getById(assignToId).ifPresent(exec -> {
					lead.setAssignedTo(exec);
					// Store username directly for DB visibility
					String username = exec.getUserId() != null
							? userRepository.findById(exec.getUserId()).map(u -> u.getUsername()).orElse(exec.getEmail())
							: exec.getEmail();
					lead.setAssignedToUsername(username);
				});
			}

			leadService.saveLead(lead);
			ra.addFlashAttribute("success", "Lead \"" + customerName + "\" submitted for admin approval.");
		} catch (Exception e) {
			ra.addFlashAttribute("error", "Failed to submit lead: " + e.getMessage());
		}
		return "redirect:/manager/dashboard";
	}

	// ── Edit an existing PENDING lead ─────────────────────────────────────
	@PostMapping("/leads/{id}/edit")
	public String editLead(
			@PathVariable Long id,
			@RequestParam String customerName,
			@RequestParam(required = false) String company,
			@RequestParam(required = false) String email,
			@RequestParam(required = false) String phone,
			@RequestParam(required = false, defaultValue = "0") BigDecimal dealValue,
			@RequestParam(required = false, defaultValue = "OTHER") String source,
			@RequestParam(required = false) String notes,
			@RequestParam(required = false) Long assignToId,
			RedirectAttributes ra) {
		try {
			leadService.getById(id).ifPresent(lead -> {
				if (lead.getStatus() != Lead.LeadStatus.PENDING && lead.getStatus() != Lead.LeadStatus.REJECTED) {
					throw new IllegalStateException("Only PENDING or REJECTED leads can be edited.");
				}
				lead.setCustomerName(customerName);
				lead.setCompany(company);
				lead.setEmail(email);
				lead.setPhone(phone);
				lead.setDealValue(dealValue != null ? dealValue : BigDecimal.ZERO);
				try { lead.setSource(Lead.Source.valueOf(source)); } catch (Exception e) { lead.setSource(Lead.Source.OTHER); }
				lead.setNotes(notes);
				lead.setStatus(Lead.LeadStatus.PENDING); // re-submit for approval
				lead.setRejectionNote(null);
				if (assignToId != null) {
					empService.getById(assignToId).ifPresent(lead::setAssignedTo);
				}
				leadService.saveLead(lead);
			});
			ra.addFlashAttribute("success", "Lead updated and re-submitted for approval.");
		} catch (Exception e) {
			ra.addFlashAttribute("error", "Failed to update lead: " + e.getMessage());
		}
		return "redirect:/manager/leads/assigned";
	}

	// ════════════════════════════════════════════════════════════════════════
	// SALES PIPELINE
	// ════════════════════════════════════════════════════════════════════════

	@GetMapping("/pipeline/deals")
	public String dealTracking(Model model, Principal principal) {
		String t = tid(principal);
		model.addAttribute("approvedValue", leadService.sumApprovedValueByTenant(t));
		model.addAttribute("approvedLeads", leadService.countApprovedByTenant(t));
		return "manager-deal-tracking";
	}

	@GetMapping("/pipeline/stages")
	public String stageMonitoring(Model model, Principal principal) {
		String t = tid(principal);
		model.addAttribute("totalLeads",    leadService.countTotalByTenant(t));
		model.addAttribute("approvedLeads", leadService.countApprovedByTenant(t));
		return "manager-stage-monitoring";
	}

	// ════════════════════════════════════════════════════════════════════════
	// ACTIVITIES
	// ════════════════════════════════════════════════════════════════════════

	@GetMapping("/activities/calls")
	public String calls(Model model) {
		return "manager-calls";
	}

	@GetMapping("/activities/meetings")
	public String meetings(Model model) {
		return "manager-meetings";
	}

	@GetMapping("/activities/followups")
	public String followups(Model model, Principal principal) {
		model.addAttribute("pendingLeads", leadService.countPendingByTenant(tid(principal)));
		return "manager-followups";
	}

	// ════════════════════════════════════════════════════════════════════════
	// REPORTS
	// ════════════════════════════════════════════════════════════════════════

	@GetMapping("/reports/performance")
	public String teamPerformance(Model model, Principal principal) {
		String t = tid(principal);
		model.addAttribute("totalSalesExecs", empService.countSalesExecutivesByTenant(t));
		model.addAttribute("approvedLeads",   leadService.countApprovedByTenant(t));
		return "manager-team-performance";
	}

	@GetMapping("/reports/revenue")
	public String revenue(Model model, Principal principal) {
		String t = tid(principal);
		model.addAttribute("approvedValue", leadService.sumApprovedValueByTenant(t));
		model.addAttribute("approvedLeads", leadService.countApprovedByTenant(t));
		return "manager-revenue";
	}

	@GetMapping("/reports/conversion")
	public String conversionReports(Model model, Principal principal) {
		String t = tid(principal);
		model.addAttribute("totalLeads",    leadService.countTotalByTenant(t));
		model.addAttribute("approvedLeads", leadService.countApprovedByTenant(t));
		model.addAttribute("pendingLeads",  leadService.countPendingByTenant(t));
		return "manager-conversion-reports";
	}

	// ════════════════════════════════════════════════════════════════════════
	// SYSTEM
	// ════════════════════════════════════════════════════════════════════════

	@GetMapping("/calendar")
	public String calendar(Model model) {
		return "manager-calendar";
	}

	@GetMapping("/notifications")
	public String notifications(Model model, Principal principal) {
		String t = tid(principal);
		model.addAttribute("myLeads",      leadService.getAllLeadsByTenant(t));
		model.addAttribute("pendingCount", leadService.countPendingByTenant(t));
		return "manager-notifications";
	}

	@GetMapping("/profile")
	public String profile(Model model, Principal principal) {
		String t = tid(principal);
		model.addAttribute("totalLeads",    leadService.countTotalByTenant(t));
		model.addAttribute("approvedLeads", leadService.countApprovedByTenant(t));
		model.addAttribute("approvedValue", leadService.sumApprovedValueByTenant(t));
		return "manager-profile";
	}
}
